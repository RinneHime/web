package cn.lili.modules.promotion.service;

import cn.lili.common.trigger.message.PromotionMessage;
import cn.lili.modules.search.entity.dos.EsGoodsIndex;

import java.util.Map;

/**
 * 促销业务层
 *
 * @author Chopper
 * @date 2020/11/18 9:45 上午
 */
public interface PromotionService {


    /**
     * 更新促销活动状态
     *
     * @param promotionMessage 促销变更信息
     * @return 是否更新成功
     */
    boolean updatePromotionStatus(PromotionMessage promotionMessage);


    /**
     * 获取当前进行的所有促销活动信息
     *
     * @return 当前促销活动集合
     */
    Map<String, Object> getCurrentPromotion();

    /**
     * 根据商品索引获取当前商品索引的所有促销活动信息
     *
     * @param index 商品索引
     * @return 当前促销活动集合
     */
    Map<String, Object> getGoodsCurrentPromotionMap(EsGoodsIndex index);

}