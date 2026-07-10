package org.example.quickcode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import org.example.quickcode.model.dto.user.UserPasswordUpdateRequest;
import org.example.quickcode.model.dto.user.UserProfileUpdateRequest;
import org.example.quickcode.model.dto.user.UserQueryRequest;
import org.springframework.web.multipart.MultipartFile;
import org.example.quickcode.model.entity.User;
import org.example.quickcode.model.vo.LoginUserVO;
import org.example.quickcode.model.vo.UserVO;


import java.util.List;

/**
 * 用户 服务层。
 *
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 获取加密密码
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request      请求
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取脱敏的已登录用户信息
     * @param user 用户
     * @return 脱敏后的用户信息
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏的用户信息
     * @param user 用户
     * @return 脱敏后的用户信息
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏用户列表
     * @param userList
     * @return
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取当前登录用户
     * @param request 请求
     * @return 用户
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     * @param request 请求
     * @return 退出成功
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取查询条件
     * @param userQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 更新当前登录用户资料
     */
    LoginUserVO updateMyProfile(UserProfileUpdateRequest userProfileUpdateRequest, HttpServletRequest request);

    /**
     * 修改当前登录用户密码
     */
    boolean updatePassword(UserPasswordUpdateRequest userPasswordUpdateRequest, HttpServletRequest request);

    /**
     * 上传当前登录用户头像
     */
    String uploadAvatar(MultipartFile file, HttpServletRequest request);
}
