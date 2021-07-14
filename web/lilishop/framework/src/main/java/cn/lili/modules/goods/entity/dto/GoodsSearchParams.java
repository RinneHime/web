package cn.lili.modules.goods.entity.dto;

import cn.lili.common.utils.StringUtils;
import cn.lili.common.vo.PageVO;
import cn.lili.modules.goods.entity.enums.GoodsAuthEnum;
import cn.lili.modules.goods.entity.enums.GoodsStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 商品查询条件
 *
 * @author pikachu
 * @date 2020-02-24 19:27:20
 */
@Data
public class GoodsSearchParams extends PageVO {

    private static final long serialVersionUID = 2544015852728566887L;

    @ApiModelProperty(value = "商品编号")
    private String goodsId;

    @ApiModelProperty(value = "商品名称")
    private String goodsName;

    @ApiModelProperty(value = "商品编号")
    private String sn;

    @ApiModelProperty(value = "商家ID")
    private String storeId;

    @ApiModelProperty(value = "卖家名字")
    private String storeName;

    @ApiModelProperty(value = "价格,可以为范围，如10_1000")
    private String price;

    @ApiModelProperty(value = "分类path")
    private String categoryPath;

    @ApiModelProperty(value = "是否是积分商品")
    private Boolean isPoint;

    @ApiModelProperty(value = "店铺分类id")
    private String storeCategoryPath;

    @ApiModelProperty(value = "是否自营")
    private Boolean selfOperated;

    /**
     * @see GoodsStatusEnum
     */
    @ApiModelProperty(value = "上下架状态")
    private String marketEnable;

    /**
     * @see GoodsAuthEnum
     */
    @ApiModelProperty(value = "审核状态")
    private String isAuth;

    @ApiModelProperty(value = "库存数量")
    private Integer quantity;

    @ApiModelProperty(value = "是否为推荐商品")
    private Boolean recommend;

    /**
     * @see cn.lili.modules.goods.entity.enums.GoodsTypeEnum
     */
    @ApiModelProperty(value = "商品类型")
    private String goodsType;

    public <T> QueryWrapper<T> queryWrapper() {
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotEmpty(goodsId)) {
            queryWrapper.eq("goods_id", goodsId);
        }
        if (StringUtils.isNotEmpty(goodsName)) {
            queryWrapper.like("goods_name", goodsName);
        }
        if (StringUtils.isNotEmpty(sn)) {
            queryWrapper.eq("sn", sn);
        }
        if (StringUtils.isNotEmpty(storeId)) {
            queryWrapper.eq("store_id", storeId);
        }
        if (StringUtils.isNotEmpty(storeName)) {
            queryWrapper.like("store_name", storeName);
        }
        if (StringUtils.isNotEmpty(categoryPath)) {
            queryWrapper.like("category_path", categoryPath);
        }
        if (isPoint != null) {
            queryWrapper.eq("is_point", isPoint);
        }
        if (StringUtils.isNotEmpty(storeCategoryPath)) {
            queryWrapper.like("store_category_path", storeCategoryPath);
        }
        if (selfOperated != null) {
            queryWrapper.eq("self_operated", selfOperated);
        }
        if (StringUtils.isNotEmpty(marketEnable)) {
            queryWrapper.eq("market_enable", marketEnable);
        }
        if (StringUtils.isNotEmpty(isAuth)) {
            queryWrapper.eq("is_auth", isAuth);
        }
        if (quantity != null) {
            queryWrapper.le("quantity", quantity);
        }
        if (recommend != null) {
            queryWrapper.le("recommend", recommend);
        }
        if (goodsType != null) {
            queryWrapper.eq("goods_type", goodsType);
        }

        queryWrapper.eq("delete_flag", false);
        this.betweenWrapper(queryWrapper);
        return queryWrapper;
    }

    private <T> void betweenWrapper(QueryWrapper<T> queryWrapper) {
        if (StringUtils.isNotEmpty(price)) {
            String[] s = price.split("_");
            if (s.length > 1) {
                queryWrapper.ge("price", s[1]);
            } else {
                queryWrapper.le("price", s[0]);
            }
        }
    }


}
