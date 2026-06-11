package com.greenhouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.greenhouse.entity.ClassificationResult;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 作物图像分类结果 Mapper
 */
@Mapper
public interface ClassificationResultMapper extends BaseMapper<ClassificationResult> {

    /** 查询最近 N 条记录（按时间倒序） */
    @Select("SELECT * FROM gh_classification_result ORDER BY create_time DESC LIMIT #{limit}")
    List<ClassificationResult> findRecent(int limit);

    /** 按大棚 key 查询最近 N 条 */
    @Select("SELECT * FROM gh_classification_result WHERE greenhouse_key = #{greenhouseKey} ORDER BY create_time DESC LIMIT #{limit}")
    List<ClassificationResult> findRecentByGreenhouse(String greenhouseKey, int limit);

    /** 清理超过指定小时数的旧记录 */
    @Delete("DELETE FROM gh_classification_result WHERE create_time < DATE_SUB(NOW(), INTERVAL #{hours} HOUR)")
    int deleteOlderThan(int hours);
}
