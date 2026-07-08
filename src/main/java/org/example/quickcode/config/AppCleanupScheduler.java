package org.example.quickcode.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.quickcode.constant.AppConstant;
import org.example.quickcode.manager.CosManager;
import org.example.quickcode.mapper.AppMapper;
import org.example.quickcode.model.entity.App;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * 应用相关资源的定时清理任务
 * 负责批量物理清理已逻辑删除的应用所关联的资源
 */
@Component
@Slf4j
public class AppCleanupScheduler {

    @Resource
    private AppMapper appMapper;

    @Resource
    private CosManager cosManager;

    /**
     * 每周日凌晨 3 点清理已逻辑删除的应用及其关联资源
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void cleanupDeletedApps() {
        log.info("开始定时清理已逻辑删除的应用资源");
        List<App> deletedApps;
        try {
            deletedApps = appMapper.selectDeletedApps();
        } catch (Exception e) {
            log.error("查询已逻辑删除的应用失败", e);
            return;
        }

        if (deletedApps.isEmpty()) {
            log.info("无需清理，没有已逻辑删除的应用");
            return;
        }

        log.info("待清理的应用数量: {}", deletedApps.size());
        for (App app : deletedApps) {
            Long appId = app.getId();
            try {
                // 1. 清理 COS 封面图
                String coverUrl = app.getCover();
                if (StrUtil.isNotBlank(coverUrl)) {
                    cosManager.deleteObjectByUrl(coverUrl);
                }

                // 2. 清理部署目录
                String deployKey = app.getDeployKey();
                if (StrUtil.isNotBlank(deployKey)) {
                    String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
                    FileUtil.del(deployDirPath);
                }

                // 3. 清理代码生成目录
                String codeGenType = app.getCodeGenType();
                if (StrUtil.isNotBlank(codeGenType)) {
                    String codeDirName = codeGenType + "_" + appId;
                    String codeDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + codeDirName;
                    FileUtil.del(codeDirPath);
                }

                // 4. 物理删除数据库记录
                appMapper.deleteById(appId);

                log.info("已清理应用 [{}] 的所有关联资源", appId);
            } catch (Exception e) {
                log.error("清理应用 [{}] 的资源失败", appId, e);
            }
        }
        log.info("定时清理已逻辑删除的应用资源完成");
    }
}
