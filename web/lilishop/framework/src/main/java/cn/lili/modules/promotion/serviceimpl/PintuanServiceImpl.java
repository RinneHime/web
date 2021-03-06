package cn.lili.modules.promotion.serviceimpl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.trigger.util.DelayQueueTools;
import cn.lili.common.trigger.enums.DelayTypeEnums;
import cn.lili.common.trigger.message.PromotionMessage;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.trigger.interfaces.TimeTrigger;
import cn.lili.common.trigger.model.TimeExecuteConstant;
import cn.lili.common.trigger.model.TimeTriggerMsg;
import cn.lili.common.utils.DateUtil;
import cn.lili.common.utils.PageUtil;
import cn.lili.common.vo.PageVO;
import cn.lili.config.rocketmq.RocketmqCustomProperties;
import cn.lili.modules.goods.service.GoodsSkuService;
import cn.lili.modules.member.entity.dos.Member;
import cn.lili.modules.member.service.MemberService;
import cn.lili.modules.order.order.entity.dos.Order;
import cn.lili.modules.order.order.entity.enums.OrderStatusEnum;
import cn.lili.modules.order.order.entity.enums.PayStatusEnum;
import cn.lili.modules.order.order.service.OrderService;
import cn.lili.modules.promotion.entity.dos.Pintuan;
import cn.lili.modules.promotion.entity.dos.PromotionGoods;
import cn.lili.modules.promotion.entity.enums.PromotionStatusEnum;
import cn.lili.modules.promotion.entity.enums.PromotionTypeEnum;
import cn.lili.modules.promotion.entity.vos.PintuanMemberVO;
import cn.lili.modules.promotion.entity.vos.PintuanSearchParams;
import cn.lili.modules.promotion.entity.vos.PintuanShareVO;
import cn.lili.modules.promotion.entity.vos.PintuanVO;
import cn.lili.modules.promotion.mapper.PintuanMapper;
import cn.lili.modules.promotion.service.PintuanService;
import cn.lili.modules.promotion.service.PromotionGoodsService;
import cn.lili.modules.promotion.tools.PromotionTools;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ?????????????????????
 *
 * @author Chopper
 * @date 2020/8/21
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class PintuanServiceImpl extends ServiceImpl<PintuanMapper, Pintuan> implements PintuanService {

    /**
     * ????????????
     */
    @Autowired
    private TimeTrigger timeTrigger;
    /**
     * Mongo
     */
    @Autowired
    private MongoTemplate mongoTemplate;
    /**
     * ????????????
     */
    @Autowired
    private PromotionGoodsService promotionGoodsService;
    /**
     * ????????????
     */
    @Autowired
    private GoodsSkuService goodsSkuService;
    /**
     * ??????
     */
    @Autowired
    private MemberService memberService;
    /**
     * RocketMQ
     */
    @Autowired
    private RocketmqCustomProperties rocketmqCustomProperties;
    /**
     * ??????
     */
    @Autowired
    private OrderService orderService;

    @Override
    public IPage<Pintuan> getPintuanByPage(PintuanSearchParams param, PageVO page) {
        QueryWrapper<Pintuan> queryWrapper = param.wrapper();
        return page(PageUtil.initPage(page), queryWrapper);
    }

    /**
     * ???????????????????????????
     *
     * @param pintuanId ??????id
     * @return ???????????????????????????
     */
    @Override
    public List<PintuanMemberVO> getPintuanMember(String pintuanId) {
        List<PintuanMemberVO> members = new ArrayList<>();
        PintuanVO pintuan = this.getPintuanByIdFromMongo(pintuanId);
        if (pintuan == null) {
            log.error("???????????????" + pintuanId + "???????????????????????????");
            return new ArrayList<>();
        }
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getPromotionId, pintuanId)
                .eq(Order::getOrderPromotionType, PromotionTypeEnum.PINTUAN.name())
                .eq(Order::getOrderStatus, OrderStatusEnum.PAID.name())
                .eq(Order::getParentOrderSn, "");
        List<Order> orders = orderService.list(queryWrapper);
        //?????????????????????????????????????????????????????????
        for (Order order : orders) {
            Member member = memberService.getById(order.getMemberId());
            PintuanMemberVO memberVO = new PintuanMemberVO(member);
            LambdaQueryWrapper<Order> countQueryWrapper = new LambdaQueryWrapper<>();
            countQueryWrapper.eq(Order::getOrderStatus, OrderStatusEnum.PAID.name());
            countQueryWrapper.and(i -> i.eq(Order::getSn, order.getSn()).or(j -> j.eq(Order::getParentOrderSn, order.getSn())));
            //?????????????????????
            int count = orderService.count(countQueryWrapper);
            //?????????????????????
            int toBoGrouped = pintuan.getRequiredNum() - count;
            memberVO.setGroupNum(pintuan.getRequiredNum());
            memberVO.setGroupedNum(count);
            memberVO.setToBeGroupedNum(toBoGrouped);
            memberVO.setOrderSn(order.getSn());
            members.add(memberVO);
        }
        return members;
    }

    @Override
    public IPage<PintuanVO> getPintuanByPageFromMongo(PintuanSearchParams param, PageVO page) {
        IPage<PintuanVO> pintuanPage = new Page<>();
        Query query = param.mongoQuery();
        if (page != null) {
            page.setNotConvert(true);
            PromotionTools.mongoQueryPageParam(query, page);
            pintuanPage.setCurrent(page.getPageNumber());
            pintuanPage.setSize(page.getPageSize());
        }
        List<PintuanVO> pintuanVOS = mongoTemplate.find(query, PintuanVO.class);
        pintuanPage.setRecords(pintuanVOS);
        pintuanPage.setTotal(this.getPintuanByPageFromMongoCount(param));
        return pintuanPage;
    }

    /**
     * ???mongo???????????????????????????
     *
     * @param id ??????ID
     * @return ??????????????????
     */
    @Override
    public PintuanVO getPintuanByIdFromMongo(String id) {
        PintuanVO pintuanVO = mongoTemplate.findById(id, PintuanVO.class);
        if (pintuanVO == null) {
            log.error("????????????id[" + id + "]???????????????????????????");
            throw new ServiceException(ResultCode.ERROR);
        }
        return pintuanVO;
    }

    /**
     * ???mysql???????????????????????????
     *
     * @param id ????????????id
     * @return ??????????????????
     */
    @Override
    public Pintuan getPintuanById(String id) {
        Pintuan pintuan = this.getById(id);
        if (pintuan == null) {
            log.error("????????????id[" + id + "]???????????????????????????");
            throw new ServiceException(ResultCode.ERROR);
        }
        return pintuan;
    }

    /**
     * ???mongo???????????????????????????????????????
     *
     * @param param ????????????????????????
     * @return ??????
     */
    @Override
    public Long getPintuanByPageFromMongoCount(PintuanSearchParams param) {
        Query query = param.mongoQuery();
        return mongoTemplate.count(query, PintuanVO.class);
    }

    @Override
    public boolean addPintuan(PintuanVO pintuan) {
        PromotionTools.checkPromotionTime(pintuan.getStartTime().getTime(), pintuan.getEndTime().getTime());
        this.checkSamePromotion(pintuan.getStartTime(), pintuan.getEndTime(), pintuan.getStoreId(), null);
        pintuan.setPromotionStatus(PromotionStatusEnum.NEW.name());
        //?????????MYSQL???
        boolean result = this.save(pintuan);
        this.updatePintuanPromotionGoods(pintuan);
        this.mongoTemplate.save(pintuan);
        this.addPintuanStartTask(pintuan);
        return result;
    }

    @Override
    public boolean modifyPintuan(PintuanVO pintuan) {
        PintuanVO pintuanVO = this.checkExist(pintuan.getId());
        if (!pintuan.getPromotionStatus().equals(PromotionStatusEnum.NEW.name())) {
            throw new ServiceException(ResultCode.PINTUAN_EDIT_ERROR);
        }
        //??????????????????
        PromotionTools.checkPromotionTime(pintuan.getStartTime().getTime(), pintuan.getEndTime().getTime());
        //???????????????????????????????????????????????????????????????
        this.checkSamePromotion(pintuan.getStartTime(), pintuan.getEndTime(), pintuan.getStoreId(), pintuan.getId());
        boolean result = this.updateById(pintuan);
        if (pintuan.getPromotionGoodsList() != null) {
            this.updatePintuanPromotionGoods(pintuan);
        }
        this.mongoTemplate.save(pintuan);
        //??????????????????
        if (pintuan.getStartTime().getTime() != pintuanVO.getStartTime().getTime()) {
            PromotionMessage promotionMessage = new PromotionMessage(pintuan.getId(), PromotionTypeEnum.PINTUAN.name(), PromotionStatusEnum.START.name(), pintuan.getStartTime(), pintuan.getEndTime());
            //??????????????????
            this.timeTrigger.edit(TimeExecuteConstant.PROMOTION_EXECUTOR,
                    promotionMessage,
                    pintuanVO.getStartTime().getTime(),
                    pintuan.getStartTime().getTime(),
                    DelayQueueTools.wrapperUniqueKey(DelayTypeEnums.PROMOTION, (promotionMessage.getPromotionType() + promotionMessage.getPromotionId())),
                    DateUtil.getDelayTime(pintuanVO.getStartTime().getTime()),
                    rocketmqCustomProperties.getPromotionTopic());
        }
        return result;
    }

    @Override
    public boolean openPintuan(String pintuanId, Date startTime, Date endTime) {
        PintuanVO pintuan = checkExist(pintuanId);
        pintuan.setStartTime(startTime);
        pintuan.setEndTime(endTime);
        boolean result;

        long endTimeLong = endTime.getTime() / 1000;
        //???????????????????????????
        if (endTimeLong > DateUtil.getDateline()) {
            pintuan.setPromotionStatus(PromotionStatusEnum.NEW.name());
            updatePintuanPromotionGoods(pintuan);
            this.addPintuanStartTask(pintuan);
        } else {
            //????????????????????????????????????????????????
            pintuan.setPromotionStatus(PromotionStatusEnum.END.name());
        }

        pintuan.setPromotionGoodsList(new ArrayList<>());
        result = this.updateById(pintuan);
        this.mongoTemplate.save(pintuan);
        return result;
    }

    @Override
    public boolean closePintuan(String pintuanId) {
        PintuanVO pintuan = checkExist(pintuanId);

        long endTime = pintuan.getEndTime().getTime() / 1000;
        //???????????????????????????
        if (endTime > DateUtil.getDateline()) {
            //???????????????????????????????????????????????????????????????????????????
            pintuan.setPromotionStatus(PromotionStatusEnum.CLOSE.name());
        } else {
            pintuan.setPromotionStatus(PromotionStatusEnum.END.name());
            LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Order::getOrderPromotionType, PromotionTypeEnum.PINTUAN.name());
            queryWrapper.eq(Order::getPromotionId, pintuanId);
            queryWrapper.nested(i -> i.eq(Order::getPayStatus, PayStatusEnum.PAID.name()).or().eq(Order::getOrderStatus, OrderStatusEnum.PAID.name()));
            //?????????????????????????????????????????????????????????
            Map<String, List<Order>> collect = orderService.list(queryWrapper).stream().filter(i -> StrUtil.isNotEmpty(i.getParentOrderSn())).collect(Collectors.groupingBy(Order::getParentOrderSn));
            this.isOpenFictitiousPintuan(pintuan, collect);

        }
        LambdaUpdateWrapper<Pintuan> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Pintuan::getId, pintuanId).set(Pintuan::getPromotionStatus, PromotionStatusEnum.CLOSE.name());
        boolean result = this.update(updateWrapper);
        if (pintuan.getPromotionGoodsList() != null && !pintuan.getPromotionGoodsList().isEmpty()) {
            LambdaQueryWrapper<PromotionGoods> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(PromotionGoods::getPromotionId, pintuanId);
            promotionGoodsService.remove(deleteWrapper);
            pintuan.setPromotionGoodsList(new ArrayList<>());
        }
        this.removePintuanGoodsFromEs(pintuanId, pintuan.getStartTime().getTime());
        this.mongoTemplate.save(pintuan);
        return result;
    }

    /**
     * ????????????
     *
     * @param pintuanId ??????????????????
     * @return ????????????
     */
    @Override
    public boolean deletePintuan(String pintuanId) {
        PintuanVO pintuanVO = this.checkExist(pintuanId);
        pintuanVO.setDeleteFlag(true);
        if (pintuanVO.getPromotionGoodsList() != null && !pintuanVO.getPromotionGoodsList().isEmpty()) {
            LambdaQueryWrapper<PromotionGoods> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(PromotionGoods::getPromotionId, pintuanId);
            promotionGoodsService.remove(deleteWrapper);
            pintuanVO.setPromotionGoodsList(new ArrayList<>());
        }
        boolean result = this.updateById(pintuanVO);
        this.mongoTemplate.save(pintuanVO);
        this.removePintuanGoodsFromEs(pintuanId, pintuanVO.getStartTime().getTime());
        return result;
    }

    /**
     * ????????????????????????
     *
     * @param parentOrderSn ??????????????????sn
     * @param skuId         ??????skuId
     * @return ??????????????????
     */
    @Override
    public PintuanShareVO getPintuanShareInfo(String parentOrderSn, String skuId) {
        PintuanShareVO pintuanShareVO = new PintuanShareVO();
        pintuanShareVO.setPintuanMemberVOS(new ArrayList<>());
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        //????????????????????????????????????????????????????????????
        queryWrapper.eq(Order::getOrderPromotionType, PromotionTypeEnum.PINTUAN.name())
                .eq(Order::getPayStatus, OrderStatusEnum.PAID.name())
                .and(i -> i.eq(Order::getParentOrderSn, parentOrderSn).or(j -> j.eq(Order::getSn, parentOrderSn)));
        List<Order> orders = orderService.list(queryWrapper);
        this.setPintuanOrderInfo(orders, pintuanShareVO, skuId);
        //???????????????????????????sn????????????????????????????????????????????????sn?????????????????????????????????????????????????????????
        if (!orders.isEmpty() && pintuanShareVO.getPromotionGoods() == null) {
            LambdaQueryWrapper<Order> orderLambdaQueryWrapper = new LambdaQueryWrapper<>();
            //????????????????????????????????????????????????????????????
            orderLambdaQueryWrapper.eq(Order::getOrderPromotionType, PromotionTypeEnum.PINTUAN.name())
                    .eq(Order::getPayStatus, OrderStatusEnum.PAID.name())
                    .ne(Order::getSn, parentOrderSn)
                    .and(i -> i.eq(Order::getParentOrderSn, orders.get(0).getParentOrderSn()).or(j -> j.eq(Order::getSn, orders.get(0).getParentOrderSn())));
            List<Order> parentOrders = orderService.list(orderLambdaQueryWrapper);
            this.setPintuanOrderInfo(parentOrders, pintuanShareVO, skuId);

        }
        return pintuanShareVO;
    }

    /**
     * ?????????????????????????????????????????????????????????????????????
     *
     * @param orders         ????????????
     * @param pintuanShareVO ????????????
     * @param skuId          ??????skuId????????????????????????????????????
     */
    private void setPintuanOrderInfo(List<Order> orders, PintuanShareVO pintuanShareVO, String skuId) {
        for (Order order : orders) {
            Member member = memberService.getById(order.getMemberId());
            PintuanMemberVO memberVO = new PintuanMemberVO(member);
            if (StrUtil.isEmpty(order.getParentOrderSn())) {
                memberVO.setOrderSn("");
                PromotionGoods promotionGoods = promotionGoodsService.getPromotionGoods(PromotionTypeEnum.PINTUAN, order.getPromotionId(), skuId);
                if (promotionGoods == null) {
                    throw new ServiceException(ResultCode.PINTUAN_NOT_EXIST_ERROR);
                }
                pintuanShareVO.setPromotionGoods(promotionGoods);
                Pintuan pintuanById = this.getPintuanById(order.getPromotionId());
                LambdaQueryWrapper<Order> countQueryWrapper = new LambdaQueryWrapper<>();
                countQueryWrapper.eq(Order::getPayStatus, PayStatusEnum.PAID.name());
                countQueryWrapper.and(i -> i.eq(Order::getSn, order.getSn()).or(j -> j.eq(Order::getParentOrderSn, order.getSn())));
                //?????????????????????
                int count = orderService.count(countQueryWrapper);
                //?????????????????????
                int toBoGrouped = pintuanById.getRequiredNum() - count;
                memberVO.setGroupNum(pintuanById.getRequiredNum());
                memberVO.setGroupedNum(count);
                memberVO.setToBeGroupedNum(toBoGrouped);
            }
            pintuanShareVO.getPintuanMemberVOS().add(memberVO);
        }
    }

    private void checkSamePromotion(Date startTime, Date endTime, String storeId, String pintuanId) {
        QueryWrapper<Pintuan> queryWrapper = PromotionTools.checkActiveTime(startTime, endTime, PromotionTypeEnum.PINTUAN, storeId, pintuanId);
        List<Pintuan> list = this.list(queryWrapper);
        if (!list.isEmpty()) {
            throw new ServiceException(ResultCode.PROMOTION_SAME_ERROR);
        }
    }

    private void addPintuanStartTask(PintuanVO pintuan) {
        PromotionMessage promotionMessage = new PromotionMessage(pintuan.getId(), PromotionTypeEnum.PINTUAN.name(), PromotionStatusEnum.START.name(), pintuan.getStartTime(), pintuan.getEndTime());
        TimeTriggerMsg timeTriggerMsg = new TimeTriggerMsg(TimeExecuteConstant.PROMOTION_EXECUTOR,
                pintuan.getStartTime().getTime(),
                promotionMessage,
                DelayQueueTools.wrapperUniqueKey(DelayTypeEnums.PROMOTION, (promotionMessage.getPromotionType() + promotionMessage.getPromotionId())),
                rocketmqCustomProperties.getPromotionTopic());
        //???????????????????????????????????????
        this.timeTrigger.addDelay(timeTriggerMsg);
    }

    /**
     * ???es????????????????????????????????????
     *
     * @param id              ????????????ID
     * @param originStartTime ??????????????????
     */
    private void removePintuanGoodsFromEs(String id, Long originStartTime) {
        this.timeTrigger.delete(TimeExecuteConstant.PROMOTION_EXECUTOR,
                originStartTime,
                DelayQueueTools.wrapperUniqueKey(DelayTypeEnums.PROMOTION, (PromotionTypeEnum.PINTUAN.name() + id)),
                rocketmqCustomProperties.getPromotionTopic());
    }

    /**
     * ??????????????????????????????????????????????????????
     *
     * @param pintuan ??????????????????
     * @param collect ?????????????????????
     */
    private void isOpenFictitiousPintuan(PintuanVO pintuan, Map<String, List<Order>> collect) {
        //????????????
        Integer requiredNum = pintuan.getRequiredNum();

        for (Map.Entry<String, List<Order>> entry : collect.entrySet()) {
            //????????????????????????
            if (Boolean.FALSE.equals(pintuan.getFictitious()) && entry.getValue().size() < requiredNum) {
                //???????????????????????????????????????????????????????????????????????????????????????
                LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(Order::getOrderPromotionType, PromotionTypeEnum.PINTUAN.name());
                updateWrapper.eq(Order::getPromotionId, pintuan.getId());
                updateWrapper.eq(Order::getParentOrderSn, entry.getKey());
                updateWrapper.set(Order::getOrderStatus, OrderStatusEnum.CANCELLED.name());
                updateWrapper.set(Order::getCancelReason, "????????????????????????????????????????????????????????????");
                orderService.update(updateWrapper);
            } else if (Boolean.TRUE.equals(pintuan.getFictitious())) {
                this.fictitiousPintuan(entry, requiredNum);
            }
        }
    }

    /**
     * ????????????
     *
     * @param entry       ????????????
     * @param requiredNum ??????????????????
     */
    private void fictitiousPintuan(Map.Entry<String, List<Order>> entry, Integer requiredNum) {
        Map<String, List<Order>> listMap = entry.getValue().stream().collect(Collectors.groupingBy(Order::getPayStatus));
        //???????????????
        List<Order> unpaidOrders = listMap.get(PayStatusEnum.UNPAID.name());
        //???????????????????????????
        if (unpaidOrders != null && !unpaidOrders.isEmpty()) {
            for (Order unpaidOrder : unpaidOrders) {
                unpaidOrder.setOrderStatus(OrderStatusEnum.CANCELLED.name());
                unpaidOrder.setCancelReason("????????????????????????????????????????????????????????????");
            }
            orderService.updateBatchById(unpaidOrders);
        }
        List<Order> paidOrders = listMap.get(PayStatusEnum.PAID.name());
        //????????????????????????0???????????????????????????
        if (!paidOrders.isEmpty()) {
            //???????????????
            int waitNum = requiredNum - paidOrders.size();
            //??????????????????
            for (int i = 0; i < waitNum; i++) {
                Order order = new Order();
                BeanUtil.copyProperties(paidOrders.get(0), order);
                order.setMemberId("-1");
                order.setMemberName("????????????");
                orderService.save(order);
                paidOrders.add(order);
            }
            for (Order paidOrder : paidOrders) {
                paidOrder.setOrderStatus(OrderStatusEnum.UNDELIVERED.name());
            }
            orderService.updateBatchById(paidOrders);
        }
    }

    /**
     * ????????????????????????????????????
     *
     * @param pintuanId ??????id
     * @return ????????????
     */
    private PintuanVO checkExist(String pintuanId) {
        PintuanVO pintuan = mongoTemplate.findById(pintuanId, PintuanVO.class);
        if (pintuan == null) {
            throw new ServiceException(ResultCode.PINTUAN_NOT_EXIST_ERROR);
        }
        return pintuan;
    }

    /**
     * ?????????????????????????????????
     *
     * @param pintuan ????????????
     */
    private void updatePintuanPromotionGoods(PintuanVO pintuan) {

        if (pintuan.getPromotionGoodsList() != null && !pintuan.getPromotionGoodsList().isEmpty()) {
            List<PromotionGoods> promotionGoods = PromotionTools.promotionGoodsInit(pintuan.getPromotionGoodsList(), pintuan, PromotionTypeEnum.PINTUAN);
            for (PromotionGoods promotionGood : promotionGoods) {
                if (goodsSkuService.getGoodsSkuByIdFromCache(promotionGood.getSkuId()) == null) {
                    log.error("??????[" + promotionGood.getGoodsName() + "]???????????????????????????????????????");
                    throw new ServiceException();
                }
                //???????????????????????????????????????????????????
                Integer count = promotionGoodsService.findInnerOverlapPromotionGoods(PromotionTypeEnum.SECKILL.name(), promotionGood.getSkuId(), pintuan.getStartTime(), pintuan.getEndTime(), pintuan.getId());
                //?????????????????????????????????????????????????????????
                count += promotionGoodsService.findInnerOverlapPromotionGoods(PromotionTypeEnum.PINTUAN.name(), promotionGood.getSkuId(), pintuan.getStartTime(), pintuan.getEndTime(), pintuan.getId());
                if (count > 0) {
                    log.error("??????[" + promotionGood.getGoodsName() + "]??????????????????????????????????????????????????????????????????????????????????????????");
                    throw new ServiceException("??????[" + promotionGood.getGoodsName() + "]??????????????????????????????????????????????????????????????????????????????????????????");
                }
            }
            LambdaQueryWrapper<PromotionGoods> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PromotionGoods::getPromotionId, pintuan.getId()).eq(PromotionGoods::getPromotionType, PromotionTypeEnum.PINTUAN.name());
            promotionGoodsService.remove(queryWrapper);
            promotionGoodsService.saveOrUpdateBatch(promotionGoods);
        }
    }

}