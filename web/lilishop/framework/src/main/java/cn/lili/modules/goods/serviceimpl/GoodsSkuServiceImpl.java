package cn.lili.modules.goods.serviceimpl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.common.cache.Cache;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.rocketmq.RocketmqSendCallbackBuilder;
import cn.lili.common.rocketmq.tags.GoodsTagsEnum;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.utils.PageUtil;
import cn.lili.common.utils.StringUtils;
import cn.lili.config.rocketmq.RocketmqCustomProperties;
import cn.lili.modules.goods.entity.dos.Goods;
import cn.lili.modules.goods.entity.dos.GoodsSku;
import cn.lili.modules.goods.entity.dto.GoodsParamsDTO;
import cn.lili.modules.goods.entity.dto.GoodsSearchParams;
import cn.lili.modules.goods.entity.dto.GoodsSkuStockDTO;
import cn.lili.modules.goods.entity.enums.GoodsAuthEnum;
import cn.lili.modules.goods.entity.enums.GoodsStatusEnum;
import cn.lili.modules.goods.entity.vos.GoodsSkuSpecVO;
import cn.lili.modules.goods.entity.vos.GoodsSkuVO;
import cn.lili.modules.goods.entity.vos.GoodsVO;
import cn.lili.modules.goods.entity.vos.SpecValueVO;
import cn.lili.modules.goods.mapper.GoodsSkuMapper;
import cn.lili.modules.goods.service.CategoryService;
import cn.lili.modules.goods.service.GoodsGalleryService;
import cn.lili.modules.goods.service.GoodsService;
import cn.lili.modules.goods.service.GoodsSkuService;
import cn.lili.modules.member.entity.dos.FootPrint;
import cn.lili.modules.member.entity.dos.MemberEvaluation;
import cn.lili.modules.member.entity.enums.EvaluationGradeEnum;
import cn.lili.modules.member.service.MemberEvaluationService;
import cn.lili.modules.search.entity.dos.EsGoodsAttribute;
import cn.lili.modules.search.entity.dos.EsGoodsIndex;
import cn.lili.modules.search.service.EsGoodsIndexService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品sku业务层实现
 *
 * @author pikachu
 * @date 2020-02-23 15:18:56
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class GoodsSkuServiceImpl extends ServiceImpl<GoodsSkuMapper, GoodsSku> implements GoodsSkuService {

    /**
     * 缓存
     */
    @Autowired
    private Cache<GoodsSku> cache;
    /**
     * 分类
     */
    @Autowired
    private CategoryService categoryService;
    /**
     * 商品相册
     */
    @Autowired
    private GoodsGalleryService goodsGalleryService;
    /**
     * 缓存
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * rocketMq
     */
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    /**
     * rocketMq配置
     */
    @Autowired
    private RocketmqCustomProperties rocketmqCustomProperties;
    /**
     * 会员评价
     */
    @Autowired
    private MemberEvaluationService memberEvaluationService;
    /**
     * 商品
     */
    private GoodsService goodsService;
    /**
     * 商品索引
     */
    private EsGoodsIndexService goodsIndexService;

    @Override
    public void add(List<Map<String, Object>> skuList, Goods goods) {
        // 检查是否需要生成索引
        List<GoodsSku> newSkuList;
        //如果有规格
        if (skuList != null && !skuList.isEmpty()) {
            // 添加商品sku
            newSkuList = this.addGoodsSku(skuList, goods);
        } else {
            throw new ServiceException(ResultCode.MUST_HAVE_GOODS_SKU);
        }

        this.updateStock(newSkuList);
        generateEsCheck(goods);
    }

    @Override
    public void update(List<Map<String, Object>> skuList, Goods goods, Boolean regeneratorSkuFlag) {
        // 是否存在规格
        if (skuList == null || skuList.isEmpty()) {
            throw new ServiceException(ResultCode.MUST_HAVE_GOODS_SKU);
        }
        List<GoodsSku> newSkuList;
        //删除旧的sku信息
        if (Boolean.TRUE.equals(regeneratorSkuFlag)) {
            List<GoodsSkuVO> goodsListByGoodsId = getGoodsListByGoodsId(goods.getId());
            List<String> oldSkuIds = new ArrayList<>();
            //删除旧索引
            for (GoodsSkuVO goodsSkuVO : goodsListByGoodsId) {
                oldSkuIds.add(goodsSkuVO.getId());
                goodsIndexService.deleteIndexById(goodsSkuVO.getId());
                cache.remove(GoodsSkuService.getCacheKeys(goodsSkuVO.getId()));
            }
            this.removeByIds(oldSkuIds);
            //删除sku相册
            goodsGalleryService.removeByIds(oldSkuIds);
            // 添加商品sku
            newSkuList = this.addGoodsSku(skuList, goods);

            //发送mq消息
            String destination = rocketmqCustomProperties.getGoodsTopic() + ":" + GoodsTagsEnum.SKU_DELETE.name();
            rocketMQTemplate.asyncSend(destination, JSONUtil.toJsonStr(oldSkuIds), RocketmqSendCallbackBuilder.commonCallback());
        } else {
            newSkuList = new ArrayList<>();
            for (Map<String, Object> map : skuList) {
                GoodsSku sku = new GoodsSku();
                //设置商品信息
                goodsInfo(sku, goods);
                //设置商品规格信息
                skuInfo(sku, goods, map, null);
                newSkuList.add(sku);
                //如果商品状态值不对，则es索引移除
                if (goods.getIsAuth().equals(GoodsAuthEnum.PASS.name()) && goods.getMarketEnable().equals(GoodsStatusEnum.UPPER.name())) {
                    goodsIndexService.deleteIndexById(sku.getId());
                }
            }
            this.updateBatchById(newSkuList);
        }
        this.updateStock(newSkuList);
        generateEsCheck(goods);
    }

    /**
     * 更新商品sku
     *
     * @param goodsSku sku信息
     */
    @Override
    public void update(GoodsSku goodsSku) {
        this.updateById(goodsSku);
        cache.remove(GoodsSkuService.getCacheKeys(goodsSku.getId()));
        cache.put(GoodsSkuService.getCacheKeys(goodsSku.getId()), goodsSku);
    }

    @Override
    public GoodsSku getGoodsSkuByIdFromCache(String id) {
        GoodsSku goodsSku = cache.get(GoodsSkuService.getCacheKeys(id));
        if (goodsSku == null) {
            goodsSku = this.getById(id);
            if (goodsSku == null) {
                return null;
            }
            cache.put(GoodsSkuService.getCacheKeys(id), goodsSku);
        }
        String quantity = stringRedisTemplate.opsForValue().get(GoodsSkuService.getStockCacheKey(id));
        if (quantity != null) {
            if (goodsSku.getQuantity().equals(Convert.toInt(quantity))) {
                goodsSku.setQuantity(Convert.toInt(quantity));
                this.updateById(goodsSku);
            }
        } else {
            stringRedisTemplate.opsForValue().set(GoodsSkuService.getStockCacheKey(id), goodsSku.getQuantity().toString());
        }

        return goodsSku;
    }

    @Override
    public Map<String, Object> getGoodsSkuDetail(String goodsId, String skuId) {
        Map<String, Object> map = new HashMap<>(16);
        GoodsSku goodsSku = this.getGoodsSkuByIdFromCache(skuId);

        GoodsVO goodsVO = goodsService.getGoodsVO(goodsId);
        if (goodsVO == null || !goodsVO.getMarketEnable().equals(GoodsStatusEnum.UPPER.name())
                || !goodsVO.getIsAuth().equals(GoodsAuthEnum.PASS.name())
                || Boolean.TRUE.equals(goodsVO.getDeleteFlag())) {
            throw new ServiceException(ResultCode.GOODS_NOT_EXIST);
        }
        //如果规格为空则使用商品ID进行查询
        if (goodsSku == null) {
            skuId = goodsVO.getSkuList().get(0).getId();
            goodsSku = this.getGoodsSkuByIdFromCache(skuId);
            //如果使用商品ID无法查询SKU则返回错误
            if (goodsSku == null) {
                throw new ServiceException(ResultCode.GOODS_NOT_EXIST);
            }
        }
        //获取当前商品的索引信息
        EsGoodsIndex goodsIndex = goodsIndexService.findById(skuId);
        if (goodsIndex == null) {
            goodsIndex = goodsIndexService.resetEsGoodsIndex(goodsSku, goodsVO.getGoodsParamsDTOList());
        }
        //商品规格
        GoodsSkuVO goodsSkuDetail = this.getGoodsSkuVO(goodsSku);

        //设置当前商品的促销价格
        if (goodsIndex.getPromotionMap() != null && !goodsIndex.getPromotionMap().isEmpty() && goodsIndex.getPromotionPrice() != null) {
            goodsSkuDetail.setPromotionPrice(goodsIndex.getPromotionPrice());
        }
        map.put("data", goodsSkuDetail);

        //获取分类
        String[] split = goodsSkuDetail.getCategoryPath().split(",");
        map.put("categoryName", categoryService.getCategoryNameByIds(Arrays.asList(split)));

        //获取规格信息
        map.put("specs", this.groupBySkuAndSpec(goodsSkuDetail.getGoodsId()));
        map.put("promotionMap", goodsIndex.getPromotionMap());

        //获取参数信息
        if (goodsVO.getGoodsParamsDTOList() != null && goodsVO.getGoodsParamsDTOList().size() > 0) {
            map.put("goodsParamsDTOList", goodsVO.getGoodsParamsDTOList());
        }

        //记录用户足迹
        if (UserContext.getCurrentUser() != null) {
            FootPrint footPrint = new FootPrint(UserContext.getCurrentUser().getId(), goodsId, skuId);
            String destination = rocketmqCustomProperties.getGoodsTopic() + ":" + GoodsTagsEnum.VIEW_GOODS.name();
            rocketMQTemplate.asyncSend(destination, footPrint, RocketmqSendCallbackBuilder.commonCallback());
        }
        return map;
    }

    @Override
    public List<GoodsSkuSpecVO> groupBySkuAndSpec(String goodsId) {
        List<GoodsSkuVO> goodsListByGoodsId = this.getGoodsListByGoodsId(goodsId);
        List<GoodsSkuSpecVO> skuSpecVOList = new ArrayList<>();
        for (GoodsSkuVO goodsSkuVO : goodsListByGoodsId) {
            GoodsSkuSpecVO specVO = new GoodsSkuSpecVO();
            specVO.setSkuId(goodsSkuVO.getId());
            specVO.setSpecValues(goodsSkuVO.getSpecList());
            specVO.setQuantity(goodsSkuVO.getQuantity());
            skuSpecVOList.add(specVO);
        }
        return skuSpecVOList;
    }

    /**
     * 更新商品sku状态
     *
     * @param goods 商品信息(Id,MarketEnable/IsAuth)
     */
    @Override
    public void updateGoodsSkuStatus(Goods goods) {
        LambdaUpdateWrapper<GoodsSku> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(GoodsSku::getGoodsId, goods.getId());
        updateWrapper.set(GoodsSku::getMarketEnable, goods.getMarketEnable());
        updateWrapper.set(GoodsSku::getIsAuth, goods.getIsAuth());
        updateWrapper.set(GoodsSku::getDeleteFlag, goods.getDeleteFlag());
        boolean update = this.update(updateWrapper);
        if (Boolean.TRUE.equals(update)) {
            List<GoodsSku> goodsSkus = this.getGoodsSkuListByGoodsId(goods.getId());
            for (GoodsSku sku : goodsSkus) {
                cache.remove(GoodsSkuService.getCacheKeys(sku.getId()));
                cache.put(GoodsSkuService.getCacheKeys(sku.getId()), sku);
            }
            generateEsCheck(goods);
        }
    }

    @Override
    public List<GoodsSku> getGoodsSkuByIdFromCache(List<String> ids) {
        List<String> keys = new ArrayList<>();
        for (String id : ids) {
            keys.add(GoodsSkuService.getCacheKeys(id));
        }
        List<GoodsSku> list = cache.multiGet(keys);
        if (list == null || list.isEmpty()) {
            list = new ArrayList<>();
            List<GoodsSku> goodsSkus = listByIds(ids);
            for (GoodsSku skus : goodsSkus) {
                cache.put(GoodsSkuService.getCacheKeys(skus.getId()), skus);
                list.add(skus);
            }
        }
        return list;
    }

    @Override
    public List<GoodsSkuVO> getGoodsListByGoodsId(String goodsId) {
        List<GoodsSku> list = this.list(new LambdaQueryWrapper<GoodsSku>().eq(GoodsSku::getGoodsId, goodsId));
        return this.getGoodsSkuVOList(list);
    }

    /**
     * 获取goodsId下所有的goodsSku
     *
     * @param goodsId 商品id
     * @return goodsSku列表
     */
    @Override
    public List<GoodsSku> getGoodsSkuListByGoodsId(String goodsId) {
        return this.list(new LambdaQueryWrapper<GoodsSku>().eq(GoodsSku::getGoodsId, goodsId));
    }

    @Override
    public List<GoodsSkuVO> getGoodsSkuVOList(List<GoodsSku> list) {
        List<GoodsSkuVO> goodsSkuVOS = new ArrayList<>();
        for (GoodsSku goodsSku : list) {
            GoodsSkuVO goodsSkuVO = this.getGoodsSkuVO(goodsSku);
            goodsSkuVOS.add(goodsSkuVO);
        }
        return goodsSkuVOS;
    }

    @Override
    public GoodsSkuVO getGoodsSkuVO(GoodsSku goodsSku) {
        //厨师还商品
        GoodsSkuVO goodsSkuVO = new GoodsSkuVO(goodsSku);
        //获取sku信息
        JSONObject jsonObject = JSONUtil.parseObj(goodsSku.getSpecs());
        //用于接受sku信息
        List<SpecValueVO> specValueVOS = new ArrayList<>();
        //用于接受sku相册
        List<String> goodsGalleryList = new ArrayList<>();
        //循环提交的sku表单
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            SpecValueVO specValueVO = new SpecValueVO();
            if ("images".equals(entry.getKey())) {
                specValueVO.setSpecName(entry.getKey());
                if (entry.getValue().toString().contains("url")) {
                    List<SpecValueVO.SpecImages> specImages = JSONUtil.toList(JSONUtil.parseArray(entry.getValue()), SpecValueVO.SpecImages.class);
                    specValueVO.setSpecImage(specImages);
                    goodsGalleryList = specImages.stream().map(SpecValueVO.SpecImages::getUrl).collect(Collectors.toList());
                }
            } else {
                specValueVO.setSpecName(entry.getKey());
                specValueVO.setSpecValue(entry.getValue().toString());
            }
            specValueVOS.add(specValueVO);
        }
        goodsSkuVO.setGoodsGalleryList(goodsGalleryList);
        goodsSkuVO.setSpecList(specValueVOS);
        return goodsSkuVO;
    }

    @Override
    public IPage<GoodsSku> getGoodsSkuByPage(GoodsSearchParams searchParams) {
        return this.page(PageUtil.initPage(searchParams), searchParams.queryWrapper());
    }

    @Override
    public void updateStocks(List<GoodsSkuStockDTO> goodsSkuStockDTOS) {
        for (GoodsSkuStockDTO goodsSkuStockDTO : goodsSkuStockDTOS) {
            this.updateStock(goodsSkuStockDTO.getSkuId(), goodsSkuStockDTO.getQuantity());
        }
    }

    @Override
    public void updateStock(String skuId, Integer quantity) {
        GoodsSku goodsSku = getGoodsSkuByIdFromCache(skuId);
        if (goodsSku != null) {
            if (quantity <= 0) {
                goodsIndexService.deleteIndexById(goodsSku.getId());
            }
            goodsSku.setQuantity(quantity);
            this.update(new LambdaUpdateWrapper<GoodsSku>().eq(GoodsSku::getId, skuId).set(GoodsSku::getQuantity, quantity));
            cache.put(GoodsSkuService.getCacheKeys(skuId), goodsSku);
            stringRedisTemplate.opsForValue().set(GoodsSkuService.getStockCacheKey(skuId), quantity.toString());

            //更新商品库存
            List<GoodsSku> goodsSkus = new ArrayList<>();
            goodsSkus.add(goodsSku);
            this.updateGoodsStuck(goodsSkus);
        }
    }

    @Override
    public Integer getStock(String skuId) {
        String cacheKeys = GoodsSkuService.getStockCacheKey(skuId);
        String stockStr = stringRedisTemplate.opsForValue().get(cacheKeys);
        if (stockStr != null) {
            return Convert.toInt(stockStr);
        } else {
            GoodsSku goodsSku = getGoodsSkuByIdFromCache(skuId);
            stringRedisTemplate.opsForValue().set(cacheKeys, goodsSku.getQuantity().toString());
            return goodsSku.getQuantity();
        }
    }

    @Override
    public void updateGoodsStuck(List<GoodsSku> goodsSkus) {
        //商品id集合 hashset 去重复
        Set<String> goodsIds = new HashSet<>();
        for (GoodsSku sku : goodsSkus) {
            goodsIds.add(sku.getGoodsId());
        }
        //获取相关的sku集合
        LambdaQueryWrapper<GoodsSku> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.in(GoodsSku::getGoodsId, goodsIds);
        List<GoodsSku> goodsSkuList = this.list(lambdaQueryWrapper);

        //统计每个商品的库存
        for (String goodsId : goodsIds) {
            //库存
            Integer quantity = 0;
            for (GoodsSku goodsSku : goodsSkuList) {
                if (goodsId.equals(goodsSku.getGoodsId())) {
                    quantity += goodsSku.getQuantity();
                }
            }
            //保存商品库存结果     这里在for循环中调用数据库保存不太好，需要优化
            goodsService.updateStock(goodsId, quantity);
        }


    }

    @Override
    public void updateGoodsSkuCommentNum(String skuId) {
        //获取商品信息
        GoodsSku goodsSku = this.getGoodsSkuByIdFromCache(skuId);

        LambdaQueryWrapper<MemberEvaluation> goodEvaluationQueryWrapper = new LambdaQueryWrapper<>();
        goodEvaluationQueryWrapper.eq(MemberEvaluation::getSkuId, goodsSku.getId());
        goodEvaluationQueryWrapper.eq(MemberEvaluation::getGrade, EvaluationGradeEnum.GOOD.name());

        //好评数量
        int highPraiseNum = memberEvaluationService.count(goodEvaluationQueryWrapper);

        //更新商品评价数量
        goodsSku.setCommentNum(goodsSku.getCommentNum() != null ? goodsSku.getCommentNum() + 1 : 1);

        //好评率
        double grade = NumberUtil.mul(NumberUtil.div(highPraiseNum, goodsSku.getCommentNum().doubleValue(), 2), 100);
        goodsSku.setGrade(grade);
        //修改规格
        this.update(goodsSku);
        //修改规格索引
        goodsIndexService.updateIndexCommentNum(goodsSku.getId(), goodsSku.getCommentNum(), highPraiseNum, grade);

        //修改商品的评价数量
        goodsService.updateGoodsCommentNum(goodsSku.getGoodsId());
    }

    /**
     * 生成ES商品索引
     *
     * @param goods 商品信息
     */
    private void generateEsCheck(Goods goods) {
        //如果商品通过审核&&并且已上架
        List<GoodsSku> goodsSkuList = this.list(new LambdaQueryWrapper<GoodsSku>().eq(GoodsSku::getGoodsId, goods.getId()));
        if (goods.getIsAuth().equals(GoodsAuthEnum.PASS.name())
                && goods.getMarketEnable().equals(GoodsStatusEnum.UPPER.name())
                && Boolean.FALSE.equals(goods.getDeleteFlag())) {
            List<EsGoodsIndex> goodsIndices = new ArrayList<>();
            for (GoodsSku goodsSku : goodsSkuList) {
                EsGoodsIndex esGoodsOld = goodsIndexService.findById(goodsSku.getId());
                EsGoodsIndex goodsIndex = new EsGoodsIndex(goodsSku);
                if (goods.getParams() != null && !goods.getParams().isEmpty()) {
                    List<GoodsParamsDTO> goodsParamDTOS = JSONUtil.toList(goods.getParams(), GoodsParamsDTO.class);
                    goodsIndex = new EsGoodsIndex(goodsSku, goodsParamDTOS);
                }
                //如果商品库存不为0，并且es中有数据
                if (goodsSku.getQuantity() > 0 && esGoodsOld == null) {
                    goodsIndices.add(goodsIndex);
                } else if (goodsSku.getQuantity() > 0 && esGoodsOld != null) {
                    goodsIndexService.updateIndex(goodsIndex);
                }
            }
            if (!goodsIndices.isEmpty()) {
                String destination = rocketmqCustomProperties.getGoodsTopic() + ":" + GoodsTagsEnum.GENERATOR_GOODS_INDEX.name();
                //发送mq消息
                rocketMQTemplate.asyncSend(destination, JSONUtil.toJsonStr(goodsIndices), RocketmqSendCallbackBuilder.commonCallback());
            }
        }
        //如果商品状态值不支持es搜索，那么将商品信息做下架处理
        else {
            for (GoodsSku goodsSku : goodsSkuList) {
                EsGoodsIndex esGoodsOld = goodsIndexService.findById(goodsSku.getId());
                if (esGoodsOld != null) {
                    goodsIndexService.deleteIndexById(goodsSku.getId());
                }
            }
        }
    }

    /**
     * 修改库存
     *
     * @param goodsSkus 商品SKU
     */
    private void updateStock(List<GoodsSku> goodsSkus) {
        //总库存数量
        Integer quantity = 0;
        for (GoodsSku sku : goodsSkus) {
            this.updateStock(sku.getId(), sku.getQuantity());
            quantity += sku.getQuantity();
        }
        //修改商品库存
        goodsService.updateStock(goodsSkus.get(0).getGoodsId(), quantity);
    }


    /**
     * 增加sku集合
     *
     * @param skuList sku列表
     * @param goods   商品信息
     */
    private List<GoodsSku> addGoodsSku(List<Map<String, Object>> skuList, Goods goods) {
        List<GoodsSku> skus = new ArrayList<>();
        for (Map<String, Object> skuVO : skuList) {
            Map<String, Object> resultMap = this.add(skuVO, goods);
            GoodsSku goodsSku = (GoodsSku) resultMap.get("goodsSku");
            if (goods.getSelfOperated() != null) {
                goodsSku.setSelfOperated(goods.getSelfOperated());
            }
            goodsSku.setGoodsType(goods.getGoodsType());
            skus.add(goodsSku);
            stringRedisTemplate.opsForValue().set(GoodsSkuService.getStockCacheKey(goodsSku.getId()), goodsSku.getQuantity().toString());
        }
        this.saveBatch(skus);
        return skus;
    }

    /**
     * 添加商品规格
     *
     * @param map   规格属性
     * @param goods 商品
     * @return 规格商品
     */
    private Map<String, Object> add(Map<String, Object> map, Goods goods) {
        Map<String, Object> resultMap = new HashMap<>(2);
        GoodsSku sku = new GoodsSku();

        //商品索引
        EsGoodsIndex esGoodsIndex = new EsGoodsIndex();

        //设置商品信息
        goodsInfo(sku, goods);
        //设置商品规格信息
        skuInfo(sku, goods, map, esGoodsIndex);

        esGoodsIndex.setGoodsSku(sku);
        resultMap.put("goodsSku", sku);
        resultMap.put("goodsIndex", esGoodsIndex);
        return resultMap;
    }

    /**
     * 设置规格商品的商品信息
     *
     * @param sku   规格
     * @param goods 商品
     */
    private void goodsInfo(GoodsSku sku, Goods goods) {
        //商品基本信息
        sku.setGoodsId(goods.getId());

        sku.setSellingPoint(goods.getSellingPoint());
        sku.setCategoryPath(goods.getCategoryPath());
        sku.setBrandId(goods.getBrandId());
        sku.setMarketEnable(goods.getMarketEnable());
        sku.setIntro(goods.getIntro());
        sku.setMobileIntro(goods.getMobileIntro());
        sku.setGoodsUnit(goods.getGoodsUnit());
        //商品状态
        sku.setIsAuth(goods.getIsAuth());
        sku.setSalesModel(goods.getSalesModel());
        //卖家信息
        sku.setStoreId(goods.getStoreId());
        sku.setStoreName(goods.getStoreName());
        sku.setStoreCategoryPath(goods.getStoreCategoryPath());
        sku.setFreightTemplateId(goods.getTemplateId());
    }

    /**
     * 设置商品规格信息
     *
     * @param sku          规格商品
     * @param goods        商品
     * @param map          规格信息
     * @param esGoodsIndex 商品索引
     */
    private void skuInfo(GoodsSku sku, Goods goods, Map<String, Object> map, EsGoodsIndex esGoodsIndex) {

        //规格简短信息
        StringBuilder simpleSpecs = new StringBuilder();
        //商品名称
        StringBuilder goodsName = new StringBuilder(goods.getGoodsName());
        //规格商品缩略图
        String thumbnail = "";
        String small = "";
        //规格值
        Map<String, Object> specMap = new HashMap<>(16);
        //商品属性
        List<EsGoodsAttribute> attributes = new ArrayList<>();

        //获取规格信息
        for (Map.Entry<String, Object> spec : map.entrySet()) {
            //保存规格信息
            if (("id").equals(spec.getKey()) || ("sn").equals(spec.getKey()) || ("cost").equals(spec.getKey())
                    || ("price").equals(spec.getKey()) || ("quantity").equals(spec.getKey())
                    || ("weight").equals(spec.getKey())) {
                continue;
            } else {
                specMap.put(spec.getKey(), spec.getValue());
                if (("images").equals(spec.getKey())) {
                    //设置规格商品缩略图
                    List<Map<String, String>> images = (List<Map<String, String>>) spec.getValue();
                    if (images == null || images.isEmpty()) {
                        continue;
                    }
                    //设置规格商品缩略图
                    //如果规格没有图片，则用商品图片复盖。有则增加规格图片，放在商品图片集合之前
                    if (spec.getValue() != null && StringUtils.isNotEmpty(spec.getValue().toString())) {
                        thumbnail = goodsGalleryService.getGoodsGallery(images.get(0).get("url")).getThumbnail();
                        small = goodsGalleryService.getGoodsGallery(images.get(0).get("url")).getSmall();
                    }
                } else {
                    if (spec.getValue() != null) {
                        //设置商品名称
                        goodsName.append(" ").append(spec.getValue());
                        //规格简短信息
                        simpleSpecs.append(" ").append(spec.getValue());
                    }
                }
            }
        }
        //设置规格信息
        sku.setGoodsName(goodsName.toString());
        sku.setThumbnail(thumbnail);
        sku.setSmall(small);

        //规格信息
        sku.setId(Convert.toStr(map.get("id"), ""));
        sku.setSn(Convert.toStr(map.get("sn")));
        sku.setWeight(Convert.toDouble(map.get("weight"), 0D));
        sku.setPrice(Convert.toDouble(map.get("price"), 0D));
        sku.setCost(Convert.toDouble(map.get("cost"), 0D));
        sku.setQuantity(Convert.toInt(map.get("quantity"), 0));
        sku.setSpecs(JSONUtil.toJsonStr(specMap));
        sku.setSimpleSpecs(simpleSpecs.toString());

        if (esGoodsIndex != null) {
            //商品索引
            esGoodsIndex.setAttrList(attributes);
        }
    }

    @Autowired
    public void setGoodsService(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

    @Autowired
    public void setGoodsIndexService(EsGoodsIndexService goodsIndexService) {
        this.goodsIndexService = goodsIndexService;
    }
}
