package cn.lili.modules.distribution.service;

import cn.lili.modules.distribution.entity.dos.DistributionSelectedGoods;
import com.baomidou.mybatisplus.extension.service.IService;
/**
 * 分销选择商品业务层
 *
 * @author pikachu
 * @date 2020-03-24 10:46:33
 */
public interface DistributionSelectedGoodsService extends IService<DistributionSelectedGoods> {

    /**
     * 分销员添加分销商品
     * @param distributionGoodsId 分销商品ID
     * @return
     */
    boolean add(String distributionGoodsId);

    /**
     * 分销员添加分销商品
     * @param distributionGoodsId 分销商品ID
     * @return
     */
    boolean delete(String distributionGoodsId);
}
