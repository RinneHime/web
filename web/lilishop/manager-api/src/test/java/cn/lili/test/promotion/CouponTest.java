package cn.lili.test.promotion;

import cn.lili.common.vo.PageVO;
import cn.lili.config.rocketmq.RocketmqCustomProperties;
import cn.lili.modules.goods.entity.enums.GoodsStatusEnum;
import cn.lili.modules.goods.service.GoodsSkuService;
import cn.lili.modules.promotion.entity.dos.Coupon;
import cn.lili.modules.promotion.entity.dos.PromotionGoods;
import cn.lili.modules.promotion.entity.enums.*;
import cn.lili.modules.promotion.entity.vos.CouponSearchParams;
import cn.lili.modules.promotion.entity.vos.CouponVO;
import cn.lili.modules.promotion.service.CouponService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * @author paulG
 * @since 2020/10/29
 **/
@RunWith(SpringRunner.class)
@SpringBootTest
class CouponTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private GoodsSkuService goodsSkuService;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private RocketmqCustomProperties rocketmqCustomProperties;

    @Test
    void addCoupon() {
        CouponVO couponVO = new CouponVO();
        couponVO.setCouponName("Coupon V" + couponVO.getId());
        couponVO.setCouponType(CouponTypeEnum.DISCOUNT.name());
        couponVO.setDescription(couponVO.getCouponName() + " are expensive");
        couponVO.setGetType(CouponGetEnum.FREE.name());
        couponVO.setPromotionStatus(PromotionStatusEnum.NEW.name());
//       couponVO.setStoreId("0");
//       couponVO.setStoreName("platform");
        couponVO.setStoreId("131");
        couponVO.setStoreName("小米自营旗舰店");
        couponVO.setPublishNum(1000);
        couponVO.setCouponLimitNum(0);
        couponVO.setConsumeThreshold(500D);
//       couponVO.setPrice(200D);
        couponVO.setCouponDiscount(0.1D);

        couponVO.setScopeType(CouponScopeTypeEnum.PORTION_GOODS.name());
        couponVO.setScopeId("121");
        couponVO.setStartTime(cn.hutool.core.date.DateUtil.parse("2020-11-30 15:58:00"));
        couponVO.setEndTime(cn.hutool.core.date.DateUtil.parse("2020-12-30 23:50:00"));

        if (couponVO.getCouponType().equals(CouponTypeEnum.DISCOUNT.name())) {
            couponVO.setPromotionName(couponVO.getCouponDiscount() + "折券");
        } else {
            couponVO.setPromotionName(couponVO.getPrice() + "元券");
        }
        List<PromotionGoods> promotionGoodsList = new ArrayList<>();
//       GoodsSku sku121 = goodsSkuService.getGoodsSkuByIdFromCache("121");
        PromotionGoods promotionGoods = new PromotionGoods();
        promotionGoods.setPrice(0.0);
        promotionGoods.setLimitNum(0);
        promotionGoods.setNum(1000);
        promotionGoods.setStartTime(couponVO.getStartTime());
        promotionGoods.setEndTime(couponVO.getEndTime());
        promotionGoods.setTitle(couponVO.getPromotionName());
        promotionGoods.setPromotionId(couponVO.getId());
        promotionGoods.setQuantity(1000);
        promotionGoods.setPromotionStatus(couponVO.getPromotionStatus());
        promotionGoods.setPromotionType(PromotionTypeEnum.COUPON.name());
        promotionGoodsList.add(promotionGoods);
//
//       GoodsSku sku50112 = goodsSkuService.getGoodsSkuByIdFromCache("50112");
//       promotionGoods = new PromotionGoods(sku50112);
//       promotionGoods.setPrice(80000d);
//       promotionGoods.setLimitNum(0);
//       promotionGoods.setPromotionQuantity(1000);
//       promotionGoods.setNum(1000);
//       promotionGoods.setStartTime(couponVO.getStartTime());
//       promotionGoods.setEndTime(couponVO.getEndTime());
//       promotionGoods.setTitle(couponVO.getPromotionName());
//       promotionGoods.setPromotionStatus(couponVO.getPromotionStatus());
//       promotionGoodsList.add(promotionGoods);
//
        couponVO.setPromotionGoodsList(promotionGoodsList);
        Assertions.assertNotNull(couponService.add(couponVO));
    }

    @Test
    void update() {
        CouponVO couponVO = new CouponVO();
        couponVO.setId("1326081397400297472");
        couponVO.setCouponName("Coupon V" + couponVO.getId());
        couponVO.setCouponType(CouponTypeEnum.DISCOUNT.name());
        couponVO.setDescription(couponVO.getId() + " is expensive");
        couponVO.setGetType(CouponGetEnum.FREE.name());
        couponVO.setPromotionStatus(PromotionStatusEnum.START.name());
        couponVO.setStoreId("132");
        couponVO.setStoreName("联想自营旗舰店");
        couponVO.setStoreCommission(99.99D);
        couponVO.setPublishNum(1000);
        couponVO.setCouponLimitNum(0);
        couponVO.setCouponDiscount(10D);
        couponVO.setPrice(0D);

        couponVO.setScopeType(CouponScopeTypeEnum.PORTION_GOODS.name());
        couponVO.setScopeId("134,133");
        couponVO.setStartTime(cn.hutool.core.date.DateUtil.parse("2020-11-10 17:01:00"));
        couponVO.setEndTime(cn.hutool.core.date.DateUtil.parse("2020-11-10 17:10:00"));

        if (couponVO.getCouponType().equals(CouponTypeEnum.DISCOUNT.name())) {
            couponVO.setPromotionName(couponVO.getCouponDiscount() + "折券");
        } else {
            couponVO.setPromotionName(couponVO.getPrice() + "元券");
        }

        List<PromotionGoods> promotionGoodsList = new ArrayList<>();
        PromotionGoods promotionGoods = new PromotionGoods();
        promotionGoods.setSkuId("134");
        promotionGoods.setGoodsName("联想(Lenovo)YOGA S740商务办公本 英特尔酷睿i5 14英寸超轻薄笔记本电脑(i5 16G 512G 独显 雷电3 WiFi6)灰");
        promotionGoods.setPrice(20000d);
        promotionGoods.setStoreId("132");
        promotionGoods.setStoreName("联想自营旗舰店");
        promotionGoods.setLimitNum(0);
        promotionGoods.setQuantity(1000);
        promotionGoods.setThumbnail("thumbnail");
        promotionGoods.setNum(1000);
        promotionGoods.setStartTime(couponVO.getStartTime());
        promotionGoods.setEndTime(couponVO.getEndTime());
        promotionGoods.setTitle(couponVO.getPromotionName());
        promotionGoods.setPromotionStatus(couponVO.getPromotionStatus());
        promotionGoodsList.add(promotionGoods);

        promotionGoods = new PromotionGoods();
        promotionGoods.setSkuId("133");
        promotionGoods.setGoodsName("联想(Lenovo)小新Pro13s“锦绣前程”故宫文创版13.3英寸轻薄笔记本电脑(I5 16G 512G 2.5K 100%sRGB)");
        promotionGoods.setPrice(100000d);
        promotionGoods.setStoreId("132");
        promotionGoods.setStoreName("联想自营旗舰店");
        promotionGoods.setLimitNum(0);
        promotionGoods.setQuantity(1000);
        promotionGoods.setThumbnail("thumbnail");
        promotionGoods.setNum(1000);
        promotionGoods.setStartTime(couponVO.getStartTime());
        promotionGoods.setEndTime(couponVO.getEndTime());
        promotionGoods.setTitle(couponVO.getPromotionName());
        promotionGoods.setPromotionStatus(couponVO.getPromotionStatus());
        promotionGoodsList.add(promotionGoods);

        couponVO.setPromotionGoodsList(promotionGoodsList);
        Assertions.assertNotNull(couponService.updateCoupon(couponVO));
    }

    @Test
    void searchFromMongo() {
        CouponSearchParams queryParam = new CouponSearchParams();
        queryParam.setStoreId("");
        PageVO pageVo = new PageVO();
        pageVo.setPageNumber(0);
        pageVo.setPageSize(10);
        IPage<CouponVO> couponsByPageFromMongo = couponService.getCouponsByPageFromMongo(queryParam, pageVo);
        Assertions.assertNotNull(couponsByPageFromMongo);
        couponsByPageFromMongo.getRecords().forEach(System.out::println);
    }

    @Test
    void searchFromMysql() {
        CouponSearchParams queryParam = new CouponSearchParams();

        PageVO pageVo = new PageVO();
        pageVo.setPageNumber(0);
        pageVo.setPageSize(10);
        IPage<Coupon> coupons = couponService.getCouponsByPage(queryParam, pageVo);
        Assertions.assertNotNull(coupons);
        coupons.getRecords().forEach(System.out::println);
    }

    @Test
    void delete() {
//       Assertions.assertTrue(couponService.deleteCoupon("1326001296591577088"));
        GoodsStatusEnum goodsStatusEnum = GoodsStatusEnum.DOWN;
        System.out.println("name::  " + goodsStatusEnum.name());
        System.out.println("description::  " + goodsStatusEnum.description());
        Assertions.assertTrue(true);
    }


}
