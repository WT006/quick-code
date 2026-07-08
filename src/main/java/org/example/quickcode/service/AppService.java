package org.example.quickcode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.example.quickcode.model.dto.app.AppAddRequest;
import org.example.quickcode.model.dto.app.AppQueryRequest;
import org.example.quickcode.model.entity.App;
import org.example.quickcode.model.entity.User;
import org.example.quickcode.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 */
public interface AppService extends IService<App> {

    /**
     * 获取应用封装类
     *
     * @param app 应用
     * @return 应用封装类
     */
    AppVO getAppVO(App app);

    /**
     * 获取查询条件
     *
     * @param appQueryRequest 查询条件
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 获取应用封装类列表
     *
     * @param records 应用列表
     * @return 应用封装类列表
     */
    List<AppVO> getAppVOList(List<App> records);

    /**
     * 对话生成代码
     * @param appId
     * @param userMessage
     * @param loginuser
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String userMessage, User loginuser);

    /**
     * 应用部署
     * @param appId
     * @param loginuser
     * @return
     */
    String deployApp(Long appId, User loginuser);

    /**
     * 异步生成应用截图
     * @param appId
     * @param appUrl
     */
    void generateAppScreenshotAsync(Long appId, String appUrl);

    /**
     * 创建应用
     * @param appAddRequest
     * @param loginUser
     * @return
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);
}
