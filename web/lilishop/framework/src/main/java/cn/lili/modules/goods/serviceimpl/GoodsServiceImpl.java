package cn.lili.modules.goods.serviceimpl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.json.JSONUtil;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.rocketmq.RocketmqSendCallbackBuilder;
import cn.lili.common.rocketmq.tags.GoodsTagsEnum;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.security.enums.UserEnums;
import cn.lili.common.utils.PageUtil;
import cn.lili.common.utils.StringUtils;
import cn.lili.config.rocketmq.RocketmqCustomProperties;
import cn.lili.modules.goods.entity.dos.Category;
import cn.lili.modules.goods.entity.dos.Goods;
import cn.lili.modules.goods.entity.dos.GoodsGallery;
import cn.lili.modules.goods.entity.dos.Parameters;
import cn.lili.modules.goods.entity.dto.GoodsOperationDTO;
import cn.lili.modules.goods.entity.dto.GoodsParamsDTO;
import cn.lili.modules.goods.entity.dto.GoodsParamsItemDTO;
import cn.lili.modules.goods.entity.dto.GoodsSearchParams;
import cn.lili.modules.goods.entity.enums.GoodsAuthEnum;
import cn.lili.modules.goods.entity.enums.GoodsStatusEnum;
import cn.lili.modules.goods.entity.vos.GoodsSkuVO;
import cn.lili.modules.goods.entity.vos.GoodsVO;
import cn.lili.modules.goods.entity.vos.ParameterGroupVO;
import cn.lili.modules.goods.mapper.GoodsMapper;
import cn.lili.modules.goods.service.*;
import cn.lili.modules.member.entity.dos.MemberEvaluation;
import cn.lili.modules.member.entity.enums.EvaluationGradeEnum;
import cn.lili.modules.member.service.MemberEvaluationService;
import cn.lili.modules.store.entity.vos.StoreVO;
import cn.lili.modules.store.service.StoreService;
import cn.lili.modules.system.entity.dos.Setting;
import cn.lili.modules.system.entity.dto.GoodsSetting;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.service.SettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ?????????????????????
 *
 * @author pikachu
 * @date 2020-02-23 15:18:56
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, Goods> implements GoodsService {


    /**
     * ??????
     */
    @Autowired
    private CategoryService categoryService;
    /**
     * ??????
     */
    @Autowired
    private SettingService settingService;
    /**
     * ????????????
     */
    @Autowired
    private GoodsGalleryService goodsGalleryService;
    /**
     * ????????????
     */
    @Autowired
    private GoodsSkuService goodsSkuService;
    /**
     * ????????????
     */
    @Autowired
    private StoreService storeService;
    /**
     * ????????????
     */
    @Autowired
    private MemberEvaluationService memberEvaluationService;
    /**
     * rocketMq
     */
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    /**
     * rocketMq??????
     */
    @Autowired
    private RocketmqCustomProperties rocketmqCustomProperties;
    /**
     * ??????-??????
     */
    @Autowired
    private CategoryParameterGroupService categoryParameterGroupService;


    @Override
    public void underStoreGoods(String storeId) {
        //????????????ID??????
        List<String> list = this.baseMapper.getGoodsIdByStoreId(storeId);
        //????????????????????????
        updateGoodsMarketAble(list, GoodsStatusEnum.DOWN, "????????????");
    }

    @Override
    public final Integer getGoodsCountByCategory(String categoryId) {
        QueryWrapper queryWrapper = Wrappers.query();
        queryWrapper.like("category_path", categoryId);
        queryWrapper.eq("delete_flag", false);
        return this.count(queryWrapper);
    }

    @Override
    public void addGoods(GoodsOperationDTO goodsOperationDTO) {
        Goods goods = new Goods(goodsOperationDTO);
        //????????????
        this.checkGoods(goods);
        //???goods????????????
        this.setGoodsGalleryParam(goodsOperationDTO.getGoodsGalleryList().get(0), goods);
        //??????????????????
        if (goodsOperationDTO.getGoodsParamsDTOList() != null && !goodsOperationDTO.getGoodsParamsDTOList().isEmpty()) {
            //??????????????????????????????
            //this.checkGoodsParams(goodsOperationDTO.getGoodsParamsDTOList(), goodsOperationDTO.getCategoryPath().substring(goodsOperationDTO.getCategoryPath().lastIndexOf(",") + 1));
            //????????????????????????
            goods.setParams(JSONUtil.toJsonStr(goodsOperationDTO.getGoodsParamsDTOList()));
        }
        //????????????
        this.save(goods);
        //????????????sku??????
        this.goodsSkuService.add(goodsOperationDTO.getSkuList(), goods);
        //????????????
        if (goodsOperationDTO.getGoodsGalleryList() != null && !goodsOperationDTO.getGoodsGalleryList().isEmpty()) {
            this.goodsGalleryService.add(goodsOperationDTO.getGoodsGalleryList(), goods.getId());
        }
    }


    @Override
    public void editGoods(GoodsOperationDTO goodsOperationDTO, String goodsId) {
        Goods goods = new Goods(goodsOperationDTO);
        goods.setId(goodsId);
        //??????????????????
        this.checkGoods(goods);
        //???goods????????????
        this.setGoodsGalleryParam(goodsOperationDTO.getGoodsGalleryList().get(0), goods);
        //??????????????????
        if (goodsOperationDTO.getGoodsParamsDTOList() != null && !goodsOperationDTO.getGoodsParamsDTOList().isEmpty()) {
            goods.setParams(JSONUtil.toJsonStr(goodsOperationDTO.getGoodsParamsDTOList()));
        }
        //????????????
        this.updateById(goods);
        //????????????sku??????
        this.goodsSkuService.update(goodsOperationDTO.getSkuList(), goods, goodsOperationDTO.getRegeneratorSkuFlag());
        //????????????
        if (goodsOperationDTO.getGoodsGalleryList() != null && !goodsOperationDTO.getGoodsGalleryList().isEmpty()) {
            this.goodsGalleryService.add(goodsOperationDTO.getGoodsGalleryList(), goods.getId());
        }

    }

    @Override
    public GoodsVO getGoodsVO(String goodsId) {
        //??????????????????
        Goods goods = this.getById(goodsId);
        if (goods == null) {
            log.error("??????ID???" + goodsId + "??????????????????");
            throw new ServiceException(ResultCode.GOODS_NOT_EXIST);
        }
        GoodsVO goodsVO = new GoodsVO();
        //??????
        BeanUtils.copyProperties(goods, goodsVO);
        //??????id
        goodsVO.setId(goods.getId());
        //??????????????????
        List<String> images = new ArrayList<>();
        List<GoodsGallery> galleryList = goodsGalleryService.goodsGalleryList(goodsId);
        for (GoodsGallery goodsGallery : galleryList) {
            images.add(goodsGallery.getOriginal());
        }
        goodsVO.setGoodsGalleryList(images);
        //??????sku??????
        List<GoodsSkuVO> goodsListByGoodsId = goodsSkuService.getGoodsListByGoodsId(goodsId);
        if (goodsListByGoodsId != null && !goodsListByGoodsId.isEmpty()) {
            goodsVO.setSkuList(goodsListByGoodsId);
        }
        //????????????????????????
        List<String> categoryName = new ArrayList<>();
        String categoryPath = goods.getCategoryPath();
        String[] strArray = categoryPath.split(",");
        List<Category> categories = categoryService.listByIds(Arrays.asList(strArray));
        for (Category category : categories) {
            categoryName.add(category.getName());
        }
        goodsVO.setCategoryName(categoryName);

        //???????????????????????????
        if (StringUtils.isNotEmpty(goods.getParams())) {
            goodsVO.setGoodsParamsDTOList(JSONUtil.toList(goods.getParams(), GoodsParamsDTO.class));
        }

        return goodsVO;
    }

    @Override
    public IPage<Goods> queryByParams(GoodsSearchParams goodsSearchParams) {
        return this.page(PageUtil.initPage(goodsSearchParams), goodsSearchParams.queryWrapper());
    }

    @Override
    public boolean auditGoods(List<String> goodsIds, GoodsAuthEnum goodsAuthEnum) {
        boolean result = false;
        for (String goodsId : goodsIds) {
            Goods goods = this.checkExist(goodsId);
            goods.setIsAuth(goodsAuthEnum.name());
            result = this.updateById(goods);
            goodsSkuService.updateGoodsSkuStatus(goods);
            //??????????????????
            String destination = rocketmqCustomProperties.getGoodsTopic() + ":" + GoodsTagsEnum.GOODS_AUDIT.name();
            //??????mq??????
            rocketMQTemplate.asyncSend(destination, JSONUtil.toJsonStr(goods.getStoreId()), RocketmqSendCallbackBuilder.commonCallback());
        }
        return result;
    }

    @Override
    public Integer goodsNum(GoodsStatusEnum goodsStatusEnum, GoodsAuthEnum goodsAuthEnum) {
        LambdaQueryWrapper<Goods> queryWrapper = Wrappers.lambdaQuery();

        queryWrapper.eq(Goods::getDeleteFlag, false);

        if (goodsStatusEnum != null) {
            queryWrapper.eq(Goods::getMarketEnable, goodsStatusEnum.name());
        }
        if (goodsAuthEnum != null) {
            queryWrapper.eq(Goods::getIsAuth, goodsAuthEnum.name());
        }
        queryWrapper.eq(StringUtils.equals(UserContext.getCurrentUser().getRole().name(), UserEnums.STORE.name()),
                Goods::getStoreId, UserContext.getCurrentUser().getStoreId());

        return this.count(queryWrapper);
    }

    @Override
    public Integer todayUpperNum() {
        LambdaQueryWrapper<Goods> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(Goods::getMarketEnable, GoodsStatusEnum.UPPER.name());
        queryWrapper.gt(Goods::getCreateTime, DateUtil.beginOfDay(new DateTime()));
        return this.count(queryWrapper);
    }

    @Override
    public Boolean updateGoodsMarketAble(List<String> goodsIds, GoodsStatusEnum goodsStatusEnum, String underReason) {
        LambdaUpdateWrapper<Goods> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.set(Goods::getMarketEnable, goodsStatusEnum.name());
        updateWrapper.set(Goods::getUnderMessage, underReason);
        updateWrapper.in(Goods::getId, goodsIds);
        this.update(updateWrapper);

        //??????????????????
        List<Goods> goodsList = this.list(new LambdaQueryWrapper<Goods>().in(Goods::getId, goodsIds));
        for (Goods goods : goodsList) {
            goodsSkuService.updateGoodsSkuStatus(goods);
        }
        return true;

    }

    @Override
    public Boolean deleteGoods(List<String> goodsIds) {

        LambdaUpdateWrapper<Goods> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.set(Goods::getMarketEnable, GoodsStatusEnum.DOWN.name());
        updateWrapper.set(Goods::getDeleteFlag, true);
        updateWrapper.in(Goods::getId, goodsIds);
        this.update(updateWrapper);

        //??????????????????
        List<Goods> goodsList = this.list(new LambdaQueryWrapper<Goods>().in(Goods::getId, goodsIds));
        for (Goods goods : goodsList) {
            //??????SKU??????
            goodsSkuService.updateGoodsSkuStatus(goods);
            //??????????????????
            String destination = rocketmqCustomProperties.getGoodsTopic() + ":" + GoodsTagsEnum.GOODS_DELETE.name();
            //??????mq??????
            rocketMQTemplate.asyncSend(destination, JSONUtil.toJsonStr(goods), RocketmqSendCallbackBuilder.commonCallback());
        }

        return true;
    }

    @Override
    public Boolean freight(List<String> goodsIds, String templateId) {
        LambdaUpdateWrapper<Goods> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
        lambdaUpdateWrapper.set(Goods::getTemplateId, templateId);
        lambdaUpdateWrapper.in(Goods::getId, goodsIds);
        return this.update(lambdaUpdateWrapper);
    }

    @Override
    public void updateStock(String goodsId, Integer quantity) {
        LambdaUpdateWrapper<Goods> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
        lambdaUpdateWrapper.set(Goods::getQuantity, quantity);
        lambdaUpdateWrapper.eq(Goods::getId, goodsId);
        this.update(lambdaUpdateWrapper);
    }

    @Override
    public void updateGoodsCommentNum(String goodsId) {

        //??????????????????
        Goods goods = this.getById(goodsId);
        //????????????????????????
        goods.setCommentNum(goods.getCommentNum() + 1);

        //?????????????????????
        LambdaQueryWrapper<MemberEvaluation> goodEvaluationQueryWrapper = new LambdaQueryWrapper<>();
        goodEvaluationQueryWrapper.eq(MemberEvaluation::getId, goodsId);
        goodEvaluationQueryWrapper.eq(MemberEvaluation::getGrade, EvaluationGradeEnum.GOOD.name());
        //????????????
        int highPraiseNum = memberEvaluationService.count(goodEvaluationQueryWrapper);
        //?????????
        double grade = NumberUtil.mul(NumberUtil.div(highPraiseNum, goods.getCommentNum().doubleValue(), 2), 100);
        goods.setGrade(grade);
        this.updateById(goods);
    }

    /**
     * ????????????????????????
     *
     * @param origin ??????
     * @param goods  ??????
     */
    private void setGoodsGalleryParam(String origin, Goods goods) {
        GoodsGallery goodsGallery = goodsGalleryService.getGoodsGallery(origin);
        goods.setOriginal(goodsGallery.getOriginal());
        goods.setSmall(goodsGallery.getSmall());
        goods.setThumbnail(goodsGallery.getThumbnail());
    }

    /**
     * ????????????????????????????????????
     *
     * @param goodsParamsDTOS ????????????
     * @param categoryId      ??????id
     */
    private void checkGoodsParams(List<GoodsParamsDTO> goodsParamsDTOS, String categoryId) {
        //?????????????????????id?????????????????????
        List<ParameterGroupVO> parameterGroupVOS = categoryParameterGroupService.getCategoryParams(categoryId);
        if (parameterGroupVOS.size() > 0) {
            //???????????????????????????
            List<Parameters> parametersList = new ArrayList<>();
            //????????????????????????????????? ?????????????????????????????????????????? ?????????????????????????????????
            for (ParameterGroupVO parameterGroupVO : parameterGroupVOS) {
                List<Parameters> parameters = parameterGroupVO.getParams();
                for (Parameters param : parameters) {
                    parametersList.add(param);
                }
            }
            List<GoodsParamsItemDTO> goodsOperationParamList = new ArrayList<>();
            //??????????????????????????????????????? ?????????????????????????????????????????? ?????????????????????????????????
            for (GoodsParamsDTO goodsParamsDTO : goodsParamsDTOS) {
                List<GoodsParamsItemDTO> goodsParamsItemDTOS = goodsParamsDTO.getGoodsParamsItemDTOList();
                for (GoodsParamsItemDTO goodsParamsItemDTO : goodsParamsItemDTOS) {
                    goodsOperationParamList.add(goodsParamsItemDTO);
                }
            }
            //??????????????????????????????
            for (Parameters parameters : parametersList) {
                for (GoodsParamsItemDTO goodsParamsItemDTO : goodsOperationParamList) {
                    if (parameters.getId().equals(goodsParamsItemDTO.getParamId())) {
                        //??????????????????????????????????????????
                        if (!parameters.getIsIndex().equals(goodsParamsItemDTO.getIsIndex())) {
                            throw new ServiceException(ResultCode.GOODS_PARAMS_ERROR);
                        }
                        //????????????????????????????????????
                        if (!parameters.getRequired().equals(goodsParamsItemDTO.getRequired())) {
                            throw new ServiceException(ResultCode.GOODS_PARAMS_ERROR);
                        }
                    }
                }
            }

        }
    }

    /**
     * ??????????????????
     * ??????????????????????????????????????????????????????
     * ???????????????????????????????????????????????????
     * ????????????????????????
     * ??????????????????????????????
     * ?????????????????????????????????
     *
     * @param goods ??????
     */
    private void checkGoods(Goods goods) {
        //??????????????????
        switch (goods.getGoodsType()) {
            case "PHYSICAL_GOODS":
                if ("0".equals(goods.getTemplateId())) {
                    throw new ServiceException(ResultCode.PHYSICAL_GOODS_NEED_TEMP);
                }
                break;
            case "VIRTUAL_GOODS":
                if (!"0".equals(goods.getTemplateId())) {
                    throw new ServiceException(ResultCode.VIRTUAL_GOODS_NOT_NEED_TEMP);
                }
                break;
            default:
                throw new ServiceException(ResultCode.GOODS_TYPE_ERROR);
        }
        //????????????????????????--?????????????????????
        if (goods.getId() != null) {
            this.checkExist(goods.getId());
        } else {
            //????????????
            goods.setCommentNum(0);
            //????????????
            goods.setBuyCount(0);
            //????????????
            goods.setQuantity(0);
            //????????????
            goods.setGrade(100.0);
        }

        //??????????????????????????????????????????
        Setting setting = settingService.get(SettingEnum.GOODS_SETTING.name());
        GoodsSetting goodsSetting = JSONUtil.toBean(setting.getSettingValue(), GoodsSetting.class);
        //??????????????????
        goods.setIsAuth(Boolean.TRUE.equals(goodsSetting.getGoodsCheck()) ? GoodsAuthEnum.TOBEAUDITED.name() : GoodsAuthEnum.PASS.name());
        //?????????????????????????????????
        if (UserContext.getCurrentUser().getRole().equals(UserEnums.STORE)) {
            StoreVO storeDetail = this.storeService.getStoreDetail();
            if (storeDetail.getSelfOperated() != null) {
                goods.setSelfOperated(storeDetail.getSelfOperated());
            }
            goods.setStoreId(storeDetail.getId());
            goods.setStoreName(storeDetail.getStoreName());
            goods.setSelfOperated(storeDetail.getSelfOperated());
        } else {
            throw new ServiceException(ResultCode.STORE_NOT_LOGIN_ERROR);
        }
    }

    /**
     * ????????????????????????
     *
     * @param goodsId
     * @return
     */
    private Goods checkExist(String goodsId) {
        Goods goods = getById(goodsId);
        if (goods == null) {
            log.error("??????ID???" + goodsId + "??????????????????");
            throw new ServiceException(ResultCode.GOODS_NOT_EXIST);
        }
        return goods;
    }

}