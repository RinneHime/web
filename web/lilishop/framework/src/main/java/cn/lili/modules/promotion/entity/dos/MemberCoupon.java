package cn.lili.modules.promotion.entity.dos;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.lili.base.BaseEntity;
import cn.lili.modules.promotion.entity.enums.CouponRangeDayEnum;
import cn.lili.modules.promotion.entity.enums.MemberCouponStatusEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

/**
 * 会员优惠券实体类
 *
 * @author Chopper
 * @date 2020-03-19 10:44 上午
 */
@Data
@Entity
@Table(name = "li_member_coupon")
@TableName("li_member_coupon")
@ApiModel(value = "会员优惠券")
public class MemberCoupon extends BaseEntity {

    private static final long serialVersionUID = -7290310311125273760L;

    @ApiModelProperty(value = "从哪个模版领取的优惠券")
    private String couponId;

    @ApiModelProperty(value = "商家id，如果是平台发送，这个值为 platform")
    private String storeId;

    @ApiModelProperty(value = "商家名称，如果是平台，这个值为 platform")
    private String storeName;

    @ApiModelProperty(value = "面额")
    private Double price;

    @ApiModelProperty(value = "折扣")
    private Double discount;

    @ApiModelProperty(value = "消费门槛")
    private Double consumeThreshold;

    @ApiModelProperty(value = "会员名称")
    private String memberName;

    @ApiModelProperty(value = "会员id")
    private String memberId;

    /**
     * @see cn.lili.modules.promotion.entity.enums.CouponScopeTypeEnum
     */
    @ApiModelProperty(value = "关联范围类型")
    private String scopeType;

    /**
     * POINT("打折"), PRICE("减免现金");
     *
     * @see cn.lili.modules.promotion.entity.enums.CouponTypeEnum
     */
    @ApiModelProperty(value = "活动类型")
    private String couponType;


    @ApiModelProperty(value = "范围关联的id")
    @Column(columnDefinition = "TEXT")
    private String scopeId;

    @ApiModelProperty(value = "使用起始时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @ApiModelProperty(value = "使用截止时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;
    /**
     * @see cn.lili.modules.promotion.entity.enums.CouponGetEnum
     */
    @ApiModelProperty(value = "优惠券类型，分为免费领取和活动赠送")
    private String getType;

    @ApiModelProperty(value = "是否是平台优惠券")
    private Boolean isPlatform;

    @ApiModelProperty(value = "店铺承担比例")
    private Double storeCommission;

    @ApiModelProperty(value = "核销时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date consumptionTime;

    /**
     * @see MemberCouponStatusEnum
     */
    @ApiModelProperty(value = "会员优惠券状态")
    private String memberCouponStatus;

    public MemberCoupon() {
    }

    public MemberCoupon(Coupon coupon) {
        setCouponId(coupon.getId());
        setStoreId(coupon.getStoreId());
        setStoreName(coupon.getStoreName());
        setPrice(coupon.getPrice());
        setDiscount(coupon.getCouponDiscount());
        setConsumeThreshold(coupon.getConsumeThreshold());
        setScopeType(coupon.getScopeType());
        setScopeId(coupon.getScopeId());
        setCouponType(coupon.getCouponType());
        setStartTime(coupon.getStartTime());

        setGetType(coupon.getGetType());
        setStoreCommission(coupon.getStoreCommission());
        if (coupon.getRangeDayType().equals(CouponRangeDayEnum.FIXEDTIME.name())) {
            setEndTime(coupon.getEndTime());
        } else {
            setEndTime(DateUtil.endOfDay(DateUtil.offset(new DateTime(), DateField.DAY_OF_YEAR, (coupon.getEffectiveDays() - 1))));
        }
    }
}