package cn.lili.modules.promotion.service;

import cn.lili.common.vo.PageVO;
import cn.lili.modules.promotion.entity.dos.SeckillApply;
import cn.lili.modules.promotion.entity.vos.SeckillApplyVO;
import cn.lili.modules.promotion.entity.vos.SeckillGoodsVO;
import cn.lili.modules.promotion.entity.vos.SeckillSearchParams;
import cn.lili.modules.promotion.entity.vos.SeckillTimelineVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 秒杀申请业务层
 *
 * @author Chopper
 * @date 2020/11/18 9:45 上午
 */
public interface SeckillApplyService extends IService<SeckillApply> {


    /**
     * 获取当天秒杀活动信息列表（时刻及对应时刻下的商品）
     *
     * @return 秒杀活动信息列表
     */
    List<SeckillTimelineVO> getSeckillTimeline();

    /**
     * 获取当天某个时刻的秒杀活动商品列表
     *
     * @param timeline 指定时刻
     * @return 秒杀活动商品列表
     */
    List<SeckillGoodsVO> getSeckillGoods(Integer timeline);

    /**
     * 从mongo中分页查询限时请购申请列表
     *
     * @param queryParam 秒杀活动申请查询参数
     * @param pageVo     分页参数
     * @return 限时请购申请列表
     */
    IPage<SeckillApply> getSeckillApplyFromMongo(SeckillSearchParams queryParam, PageVO pageVo);

    /**
     * 添加秒杀活动申请
     * 检测是否商品是否同时参加多个活动
     * 将秒杀商品信息存入秒杀活动中，更新mogo信息
     * 保存秒杀活动商品，促销商品信息
     *
     * @param seckillId        秒杀活动编号
     * @param storeId          商家id
     * @param seckillApplyList 秒杀活动申请列表
     */
    void addSeckillApply(String seckillId, String storeId, List<SeckillApplyVO> seckillApplyList);

    /**
     * 批量删除秒杀活动申请
     *
     * @param seckillId 秒杀活动活动id
     * @param ids       秒杀活动申请id集合
     */
    void removeSeckillApplyByIds(String seckillId, List<String> ids);
}