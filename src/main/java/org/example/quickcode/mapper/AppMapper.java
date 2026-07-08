package org.example.quickcode.mapper;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.example.quickcode.model.entity.App;

import java.util.List;

/**
 * 应用 映射层。
 *
 */
public interface AppMapper extends BaseMapper<App> {

    /**
     * 查询所有已逻辑删除的应用（isDelete = 1），用于定时任务批量物理清理
     */
    @Select("SELECT * FROM app WHERE isDelete = 1")
    List<App> selectDeletedApps();

}
