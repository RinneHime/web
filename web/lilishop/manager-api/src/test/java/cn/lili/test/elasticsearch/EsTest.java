package cn.lili.test.elasticsearch;

import cn.hutool.json.JSONUtil;
import cn.lili.common.vo.PageVO;
import cn.lili.modules.goods.entity.dos.GoodsSku;
import cn.lili.modules.goods.entity.enums.GoodsAuthEnum;
import cn.lili.modules.goods.entity.enums.GoodsStatusEnum;
import cn.lili.modules.goods.service.GoodsSkuService;
import cn.lili.modules.promotion.service.PromotionService;
import cn.lili.modules.search.entity.dos.EsGoodsAttribute;
import cn.lili.modules.search.entity.dos.EsGoodsIndex;
import cn.lili.modules.search.entity.dos.EsGoodsRelatedInfo;
import cn.lili.modules.search.entity.dto.EsGoodsSearchDTO;
import cn.lili.modules.search.repository.EsGoodsIndexRepository;
import cn.lili.modules.search.service.EsGoodsIndexService;
import cn.lili.modules.search.service.EsGoodsSearchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author paulG
 * @since 2020/10/14
 **/
@RunWith(SpringRunner.class)
@SpringBootTest
class EsTest {

    @Autowired
    private EsGoodsIndexService esGoodsIndexService;

    @Autowired
    private EsGoodsIndexRepository goodsIndexRepository;

    @Autowired
    private EsGoodsSearchService goodsSearchService;

    @Autowired
    private GoodsSkuService goodsSkuService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PromotionService promotionService;


    @Test
    void searchGoods() {
        EsGoodsSearchDTO goodsSearchDTO = new EsGoodsSearchDTO();
//       goodsSearchDTO.setKeyword("黄");
        goodsSearchDTO.setProp("IETF_HTTP/3");
//       goodsSearchDTO.setPrice("100_20000");
//       goodsSearchDTO.setStoreCatId(1L);
//       goodsSearchDTO.setBrandId(123L);
//       goodsSearchDTO.setCategoryId(2L);
//       goodsSearchDTO.setNameIds(Arrays.asList("1344113311566553088", "1344113367694729216"));
        PageVO pageVo = new PageVO();
        pageVo.setPageNumber(0);
        pageVo.setPageSize(100);
        pageVo.setOrder("desc");
        pageVo.setNotConvert(true);
        Page<EsGoodsIndex> esGoodsIndices = goodsSearchService.searchGoods(goodsSearchDTO, pageVo);
        Assertions.assertNotNull(esGoodsIndices);
        esGoodsIndices.getContent().forEach(System.out::println);
//       esGoodsIndices.getContent().forEach(i -> {
//           if (i.getPromotionMap() != null){
//               String s = i.getPromotionMap().keySet().parallelStream().filter(j -> j.contains(PromotionTypeEnum.FULL_DISCOUNT.name())).findFirst().orElse(null);
//               if (s != null) {
//                   FullDiscount basePromotion = (FullDiscount) i.getPromotionMap().get(s);
//                   System.out.println(basePromotion);
//               }
//           }
//       });

    }

    @Test
    void aggregationSearch() {
        EsGoodsSearchDTO goodsSearchDTO = new EsGoodsSearchDTO();
        //goodsSearchDTO.setKeyword("电脑");
        //goodsSearchDTO.setProp("颜色_故宫文创@版本_小新Pro13s");
//       goodsSearchDTO.setCategoryId("2");
//       goodsSearchDTO.setPrice("100_20000");
        PageVO pageVo = new PageVO();
        pageVo.setPageNumber(0);
        pageVo.setPageSize(10);
        pageVo.setOrder("desc");
        EsGoodsRelatedInfo selector = goodsSearchService.getSelector(goodsSearchDTO, pageVo);
        Assertions.assertNotNull(selector);
        System.out.println(JSONUtil.toJsonStr(selector));

    }

    @Test
    void init() {
        LambdaQueryWrapper<GoodsSku> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GoodsSku::getIsAuth, GoodsAuthEnum.PASS.name());
        queryWrapper.eq(GoodsSku::getMarketEnable, GoodsStatusEnum.UPPER.name());
        List<GoodsSku> list = goodsSkuService.list(queryWrapper);
        List<EsGoodsIndex> esGoodsIndices = new ArrayList<>();
        for (GoodsSku goodsSku : list) {
            EsGoodsIndex index = new EsGoodsIndex(goodsSku);
            Map<String, Object> goodsCurrentPromotionMap = promotionService.getGoodsCurrentPromotionMap(index);
            index.setPromotionMap(goodsCurrentPromotionMap);
            esGoodsIndices.add(index);
            stringRedisTemplate.opsForValue().set(GoodsSkuService.getStockCacheKey(goodsSku.getId()), goodsSku.getQuantity().toString());
        }
        esGoodsIndexService.initIndex(esGoodsIndices);
        Assertions.assertTrue(true);
    }

    @Test
    void addIndex() {
        List<EsGoodsAttribute> esGoodsAttributeList = new ArrayList<>();
        EsGoodsAttribute attribute = new EsGoodsAttribute();
        attribute.setType(0);
        attribute.setName("颜色");
        attribute.setValue("16.1英寸 6核R5 16G 512G 高色域");
        esGoodsAttributeList.add(attribute);
        attribute = new EsGoodsAttribute();
        attribute.setType(0);
        attribute.setName("版本");
        attribute.setValue("RedmiBook 18英寸 深空灰");
        esGoodsAttributeList.add(attribute);
        EsGoodsIndex goodsIndex = initGoodsIndexData("122", "0|2", "140", "142", "A142", "RedmiBook 18 锐龙版 超轻薄全面屏(6核R5-4500U 16G 512G 100% sRGB高色域)灰 手提 笔记本电脑 小米 红米 ", "131", "小米自营旗舰店", 10000D);
        goodsIndex.setAttrList(esGoodsAttributeList);

        //GoodsSku goodsSkuByIdFromCache = goodsSkuService.getGoodsSkuByIdFromCache("121");
        //EsGoodsIndex goodsIndex = new EsGoodsIndex(goodsSkuByIdFromCache);


        esGoodsIndexService.addIndex(goodsIndex);

        Assertions.assertTrue(true);
    }

    @Test
    void searchAll() {
        Iterable<EsGoodsIndex> all = goodsIndexRepository.findAll();
        Assertions.assertNotNull(all);
        all.forEach(System.out::println);
    }

    @Test
    void updateIndex() {
//       EsGoodsIndex goodsIndex = new EsGoodsIndex();
//       goodsIndex.setId("121");
//       goodsIndex.setBrandId("113");
//       goodsIndex.setGoodsId("113");
//       goodsIndex.setCategoryPath("0|1");
//       goodsIndex.setBuyCount(100);
//       goodsIndex.setCommentNum(100);
//       goodsIndex.setGoodsName("惠普（HP）战66 三代AMD版14英寸轻薄笔记本电脑（锐龙7nm 六核R5-4500U 16G 512G 400尼特高色域一年上门 ）");
//       goodsIndex.setGrade(100D);
//       goodsIndex.setHighPraiseNum(100);
//       goodsIndex.setIntro("I'd like a cup of tea, please");
//       goodsIndex.setIsAuth("1");
//       goodsIndex.setMarketEnable("1");
//       goodsIndex.setMobileIntro("I want something cold to drink");
//       goodsIndex.setPoint(100);
//       goodsIndex.setPrice(100D);
//       goodsIndex.setSelfOperated(true);
//       goodsIndex.setStoreId("113");
//       goodsIndex.setStoreName("惠普自营官方旗舰店");
//       goodsIndex.setStoreCategoryPath("1");
//       goodsIndex.setThumbnail("picture");
//       goodsIndex.setSn("A113");
//       Map<String, BasePromotion> promotionMap = new HashMap<>();
//       Coupon coupon = new Coupon();
//       coupon.setStoreId("113");
//       coupon.setStoreName("惠普自营官方旗舰店");
//       coupon.setPromotionStatus(PromotionStatusEnum.START.name());
//       coupon.setReceivedNum(0);
//       coupon.setConsumeLimit(11D);
//       coupon.setCouponLimitNum(10);
//       coupon.setCouponName("满11减10");
//       coupon.setCouponType(CouponTypeEnum.PRICE.name());
//       coupon.setGetType(CouponGetEnum.FREE.name());
//       coupon.setPrice(10D);
//       promotionMap.put(PromotionTypeEnum.COUPON.name(), coupon);
//       goodsIndex.setPromotionMap(promotionMap);
//       List<EsGoodsAttribute> esGoodsAttributeList = new ArrayList<>();
//       EsGoodsAttribute attribute = new EsGoodsAttribute();
//       attribute.setType(0);
//       attribute.setName("颜色");
//       attribute.setValue("14英寸");
//       esGoodsAttributeList.add(attribute);
//       esGoodsAttributeList.add(attribute);
//       attribute = new EsGoodsAttribute();
//       attribute.setName("版本");
//       attribute.setValue("【战66新品】R5-4500 8G 256G");
//       esGoodsAttributeList.add(attribute);
//       attribute = new EsGoodsAttribute();
//       attribute.setName("配置");
//       attribute.setValue("i5 8G 512G 2G独显");
//       esGoodsAttributeList.add(attribute);
//       goodsIndex.setAttrList(esGoodsAttributeList);
//       GoodsSku goodsSkuByIdFromCache = goodsSkuService.getGoodsSkuByIdFromCache("121");
//       EsGoodsIndex goodsIndex = new EsGoodsIndex(goodsSkuByIdFromCache);
        EsGoodsIndex byId = esGoodsIndexService.findById("121");
        byId.setPromotionMap(null);
        esGoodsIndexService.updateIndex(byId);
        Assertions.assertTrue(true);
    }

    @Test
    void deleteIndex() {
        esGoodsIndexService.deleteIndex(null);
        Assertions.assertTrue(true);
    }

    @Test
    void cleanPromotion() {
        esGoodsIndexService.cleanInvalidPromotion();
        Assertions.assertTrue(true);
    }


    private EsGoodsIndex initGoodsIndexData(String brandId, String categoryPath, String goodsId, String id, String sn, String goodsName, String storeId, String storeName, Double price) {
        EsGoodsIndex goodsIndex = new EsGoodsIndex();
        goodsIndex.setBuyCount(99);
        goodsIndex.setCommentNum(99);
        goodsIndex.setGrade(100D);
        goodsIndex.setHighPraiseNum(100);
        goodsIndex.setIntro("I'd like a cup of tea, please");
        goodsIndex.setIsAuth("1");
        goodsIndex.setMarketEnable("1");
        goodsIndex.setMobileIntro("I want something cold to drink");
        goodsIndex.setPoint(0);
        goodsIndex.setSelfOperated(true);
        goodsIndex.setThumbnail("picture");
        goodsIndex.setStoreCategoryPath("1");

        goodsIndex.setId(id);
        goodsIndex.setBrandId(brandId);
        goodsIndex.setGoodsId(goodsId);
        goodsIndex.setCategoryPath(categoryPath);
        goodsIndex.setGoodsName(goodsName);
        goodsIndex.setPrice(price);
        goodsIndex.setSn(sn);
        goodsIndex.setStoreId(storeId);
        goodsIndex.setStoreName(storeName);
        return goodsIndex;
    }


}
