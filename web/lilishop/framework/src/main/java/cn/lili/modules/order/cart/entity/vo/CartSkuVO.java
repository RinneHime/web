package cn.lili.modules.order.cart.entity.vo;

import cn.lili.modules.distribution.entity.dos.DistributionGoods;
import cn.lili.modules.goods.entity.dos.GoodsSku;
import cn.lili.modules.order.cart.entity.enums.CartTypeEnum;
import cn.lili.modules.promotion.entity.dos.PromotionGoods;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 购物车中的产品
 *
 * @author Chopper
 * @date 2020-03-24 10:33 上午
 */
@Data
@NoArgsConstructor
public class CartSkuVO extends CartBase implements Serializable {


    private static final long serialVersionUID = -894598033321906974L;


    private String sn;
    /**
     * 对应的sku DO
     */
    private GoodsSku goodsSku;

    /**
     * 分销描述
     */
    private DistributionGoods distributionGoods;

    @ApiModelProperty(value = "购买数量")
    private Integer num;

    @ApiModelProperty(value = "购买时的成交价")
    private Double purchasePrice;

    @ApiModelProperty(value = "小记")
    private Double subTotal;
    /**
     * 是否选中，要去结算 0:未选中 1:已选中，默认
     */
    @ApiModelProperty(value = "是否选中，要去结算")
    private Boolean checked;

    @ApiModelProperty(value = "是否免运费")
    private Boolean isFreeFreight;

    @ApiModelProperty(value = "积分购买 积分数量")
    private Integer point;

    @ApiModelProperty(value = "是否失效 ")
    private Boolean invalid;

    @ApiModelProperty(value = "购物车商品错误消息")
    private String errorMessage;

    @ApiModelProperty(value = "是否可配送")
    private Boolean isShip;

    @ApiModelProperty(value =
            "拼团id 如果是拼团购买 此值为拼团活动id，" +
                    "当pintuanId为空，则表示普通购买（或者拼团商品，单独购买）")
    private String pintuanId;

    @ApiModelProperty(value = "可参与的单品活动")
    private List<PromotionGoods> promotions;

    @ApiModelProperty(value = "参与促销活动更新时间(一天更新一次) 例如时间为：2020-01-01  00：00：01")
    private Date updatePromotionTime;

    /**
     * @see CartTypeEnum
     */
    @ApiModelProperty(value = "购物车类型")
    private CartTypeEnum cartType;

    /**
     * 在构造器里初始化促销列表，规格列表
     */
    public CartSkuVO(GoodsSku goodsSku) {
        this.goodsSku = goodsSku;
        this.checked = true;
        this.invalid = false;
        //默认时间为0，让系统为此商品更新缓存
        this.updatePromotionTime = new Date(0);
        this.errorMessage = "";
        this.isShip = true;
        this.purchasePrice = goodsSku.getIsPromotion() != null && goodsSku.getIsPromotion() ? goodsSku.getPromotionPrice() : goodsSku.getPrice();
        this.isFreeFreight = false;
        this.setStoreId(goodsSku.getStoreId());
        this.setStoreName(goodsSku.getStoreName());
    }
}
