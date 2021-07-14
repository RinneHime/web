package cn.lili.modules.goods.service;

import cn.lili.modules.goods.entity.dos.Goods;
import cn.lili.modules.goods.entity.dto.GoodsOperationDTO;
import cn.lili.modules.goods.entity.dto.GoodsSearchParams;
import cn.lili.modules.goods.entity.enums.GoodsAuthEnum;
import cn.lili.modules.goods.entity.enums.GoodsStatusEnum;
import cn.lili.modules.goods.entity.vos.GoodsVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 商品业务层
 *
 * @author pikachu
 * @date 2020-02-23 16:18:56
 */
public interface GoodsService extends IService<Goods> {


    /**
     * 下架所有商家商品
     *
     * @param storeId 店铺ID
     */
    void underStoreGoods(String storeId);

    /**
     * 获取某分类下的商品数量
     *
     * @param categoryId 分类ID
     * @return 商品数量
     */
    Integer getGoodsCountByCategory(String categoryId);

    /**
     * 添加商品
     *
     * @param goodsOperationDTO 商品查询条件
     */
    void addGoods(GoodsOperationDTO goodsOperationDTO);

    /**
     * 修改商品
     *
     * @param goodsOperationDTO 商品查询条件
     * @param goodsId           商品ID
     */
    void editGoods(GoodsOperationDTO goodsOperationDTO, String goodsId);

    /**
     * 查询商品VO
     *
     * @param goodsId 商品id
     * @return 商品VO
     */
    GoodsVO getGoodsVO(String goodsId);

    /**
     * 商品查询
     *
     * @param goodsSearchParams 查询参数
     * @return 商品分页
     */
    IPage<Goods> queryByParams(GoodsSearchParams goodsSearchParams);

    /**
     * 批量审核商品
     *
     * @param goodsIds      商品id列表
     * @param goodsAuthEnum 审核操作
     * @return 审核结果
     */
    boolean auditGoods(List<String> goodsIds, GoodsAuthEnum goodsAuthEnum);

    /**
     * 获取所有的已上架的商品数量
     *
     * @param goodsAuthEnum   商品审核枚举
     * @param goodsStatusEnum 商品状态枚举
     * @return 所有的已上架的商品数量
     */
    Integer goodsNum(GoodsStatusEnum goodsStatusEnum, GoodsAuthEnum goodsAuthEnum);

    /**
     * 获取今天的已上架的商品数量
     *
     * @return 今天的已上架的商品数量
     */
    Integer todayUpperNum();

    /**
     * 更新商品上架状态状态
     *
     * @param goodsIds        商品ID集合
     * @param goodsStatusEnum 更新的商品状态
     * @param underReason     下架原因
     * @return 更新结果
     */
    Boolean updateGoodsMarketAble(List<String> goodsIds, GoodsStatusEnum goodsStatusEnum, String underReason);

    /**
     * 删除商品
     *
     * @param goodsIds 商品ID
     * @return 操作结果
     */
    Boolean deleteGoods(List<String> goodsIds);

    /**
     * 设置商品运费模板
     *
     * @param goodsIds   商品列表
     * @param templateId 运费模板ID
     * @return 操作结果
     */
    Boolean freight(List<String> goodsIds, String templateId);

    /**
     * 修改商品库存数量
     *
     * @param goodsId  商品ID
     * @param quantity 库存数量
     */
    void updateStock(String goodsId, Integer quantity);

    /**
     * 更新SKU评价数量
     *
     * @param goodsId 商品ID
     */
    void updateGoodsCommentNum(String goodsId);
}