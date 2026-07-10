package org.example.quickcode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.example.quickcode.exception.BusinessException;
import org.example.quickcode.exception.ErrorCode;
import org.example.quickcode.exception.ThrowUtils;
import org.example.quickcode.manager.CosManager;
import org.example.quickcode.mapper.UserMapper;
import org.example.quickcode.model.dto.user.UserPasswordUpdateRequest;
import org.example.quickcode.model.dto.user.UserProfileUpdateRequest;
import org.example.quickcode.model.dto.user.UserQueryRequest;
import org.example.quickcode.model.entity.User;
import org.example.quickcode.model.enums.UserRoleEnum;
import org.example.quickcode.model.vo.LoginUserVO;
import org.example.quickcode.model.vo.UserVO;
import org.example.quickcode.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.example.quickcode.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService {

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;

    private static final Set<String> ALLOWED_AVATAR_SUFFIX = Set.of("jpg", "jpeg", "png", "webp");

    @Resource
    private CosManager cosManager;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        //1、校验参数
        if(StrUtil.hasBlank(userAccount, userPassword, checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        if(userAccount.length() < 4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号过短");
        }
        if(userPassword.length() < 8 || checkPassword.length() < 8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户密码过短");
        }
        if(!userPassword.equals(checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次输入的密码不一致");
        }
        //2、检查是否重复

        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if(count > 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号重复");
        }
        //3、加密密码
        String encryptPassword = getEncryptPassword(userPassword);
        //4、插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("暂无");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean save = this.save(user);
        if(!save){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"注册失败");
        }
        return user.getId();
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "wtyh";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //1、校验参数
        if(StrUtil.hasBlank(userAccount, userPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        if(userAccount.length() < 4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号过短");
        }
        if(userPassword.length() < 8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户密码过短");
        }
        //2、查询用户/密码是否存在
        String encryptPassword = getEncryptPassword(userPassword);
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if(user == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在或密码错误");
        }
        //3、设置session
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return getLoginUserVO(user);
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if(user == null){
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (!(userObj instanceof User sessionUser)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User currentUser = mapper.selectOneById(sessionUser.getId());
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("userRole", userRole)
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public LoginUserVO updateMyProfile(UserProfileUpdateRequest userProfileUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userProfileUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = getLoginUser(request);
        String userName = userProfileUpdateRequest.getUserName();
        String userProfile = userProfileUpdateRequest.getUserProfile();
        String userAvatar = userProfileUpdateRequest.getUserAvatar();

        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        if (userName != null) {
            ThrowUtils.throwIf(StrUtil.isBlank(userName), ErrorCode.PARAMS_ERROR, "用户名不能为空");
            ThrowUtils.throwIf(userName.length() > 20, ErrorCode.PARAMS_ERROR, "用户名过长");
            updateUser.setUserName(userName);
        }
        if (userProfile != null) {
            ThrowUtils.throwIf(userProfile.length() > 200, ErrorCode.PARAMS_ERROR, "简介过长");
            updateUser.setUserProfile(userProfile);
        }
        if (StrUtil.isNotBlank(userAvatar)) {
            updateUser.setUserAvatar(userAvatar);
        }

        boolean result = this.updateById(updateUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return refreshLoginSession(request, loginUser.getId());
    }

    @Override
    public boolean updatePassword(UserPasswordUpdateRequest userPasswordUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userPasswordUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = getLoginUser(request);
        String oldPassword = userPasswordUpdateRequest.getOldPassword();
        String newPassword = userPasswordUpdateRequest.getNewPassword();
        String checkPassword = userPasswordUpdateRequest.getCheckPassword();

        if (StrUtil.hasBlank(oldPassword, newPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (newPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!newPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        if (!getEncryptPassword(oldPassword).equals(loginUser.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "原密码错误");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码不能与原密码相同");
        }

        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        updateUser.setUserPassword(getEncryptPassword(newPassword));
        boolean result = this.updateById(updateUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return true;
    }

    @Override
    public String uploadAvatar(MultipartFile file, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件为空");
        ThrowUtils.throwIf(file.getSize() > MAX_AVATAR_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2MB");

        String suffix = FileUtil.getSuffix(file.getOriginalFilename());
        ThrowUtils.throwIf(StrUtil.isBlank(suffix), ErrorCode.PARAMS_ERROR, "文件格式不支持");
        suffix = suffix.toLowerCase();
        ThrowUtils.throwIf(!ALLOWED_AVATAR_SUFFIX.contains(suffix), ErrorCode.PARAMS_ERROR, "仅支持 jpg、png、webp 格式");

        String fileName = UUID.randomUUID().toString().substring(0, 8) + "." + suffix;
        String cosKey = String.format("avatar/%s/%s", loginUser.getId(), fileName);
        File tempFile = null;
        try {
            tempFile = File.createTempFile("avatar_", "." + suffix);
            file.transferTo(tempFile);
            String avatarUrl = cosManager.uploadFile(cosKey, tempFile);
            ThrowUtils.throwIf(avatarUrl == null, ErrorCode.SYSTEM_ERROR, "头像上传失败");

            if (StrUtil.isNotBlank(loginUser.getUserAvatar())) {
                cosManager.deleteObjectByUrl(loginUser.getUserAvatar());
            }

            User updateUser = new User();
            updateUser.setId(loginUser.getId());
            updateUser.setUserAvatar(avatarUrl);
            boolean result = this.updateById(updateUser);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            refreshLoginSession(request, loginUser.getId());
            return avatarUrl;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像上传失败");
        } finally {
            if (tempFile != null && tempFile.exists()) {
                FileUtil.del(tempFile);
            }
        }
    }

    private LoginUserVO refreshLoginSession(HttpServletRequest request, Long userId) {
        User updatedUser = this.getById(userId);
        ThrowUtils.throwIf(updatedUser == null, ErrorCode.NOT_LOGIN_ERROR);
        request.getSession().setAttribute(USER_LOGIN_STATE, updatedUser);
        return getLoginUserVO(updatedUser);
    }


}
