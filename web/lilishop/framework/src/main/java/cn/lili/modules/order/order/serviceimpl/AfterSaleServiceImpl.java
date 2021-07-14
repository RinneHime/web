package cn.lili.modules.order.order.serviceimpl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.lili.common.aop.syslog.annotation.SystemLogPoint;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.rocketmq.RocketmqSendCallbackBuilder;
import cn.lili.common.rocketmq.tags.AfterSaleTagsEnum;
import cn.lili.common.security.AuthUser;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.security.enums.UserEnums;
import cn.lili.common.utils.*;
import cn.lili.common.vo.PageVO;
import cn.lili.config.rocketmq.RocketmqCustomProperties;
import cn.lili.modules.order.order.aop.AfterSaleLogPoint;
import cn.lili.modules.order.order.entity.dos.AfterSale;
import cn.lili.modules.order.order.entity.dos.Order;
import cn.lili.modules.order.order.entity.dos.OrderItem;
import cn.lili.modules.order.order.entity.dto.AfterSaleDTO;
import cn.lili.modules.order.order.entity.enums.OrderItemAfterSaleStatusEnum;
import cn.lili.modules.order.order.entity.enums.OrderStatusEnum;
import cn.lili.modules.order.order.entity.enums.OrderTypeEnum;
import cn.lili.modules.order.order.entity.enums.PayStatusEnum;
import cn.lili.modules.order.order.entity.vo.AfterSaleApplyVO;
import cn.lili.modules.order.order.entity.vo.AfterSaleSearchParams;
import cn.lili.modules.order.order.entity.vo.AfterSaleVO;
import cn.lili.modules.order.order.mapper.AfterSaleMapper;
import cn.lili.modules.order.order.service.AfterSaleService;
import cn.lili.modules.order.order.service.OrderItemService;
import cn.lili.modules.order.order.service.OrderService;
import cn.lili.modules.order.trade.entity.enums.AfterSaleRefundWayEnum;
import cn.lili.modules.order.trade.entity.enums.AfterSaleStatusEnum;
import cn.lili.modules.order.trade.entity.enums.AfterSaleTypeEnum;
import cn.lili.modules.payment.kit.RefundSupport;
import cn.lili.modules.payment.kit.enums.PaymentMethodEnum;
import cn.lili.modules.statistics.model.dto.StatisticsQueryParam;
import cn.lili.modules.statistics.util.StatisticsDateUtil;
import cn.lili.modules.store.entity.dto.StoreAfterSaleAddressDTO;
import cn.lili.modules.store.entity.enums.StoreStatusEnum;
import cn.lili.modules.store.service.StoreDetailService;
import cn.lili.modules.system.entity.dos.Logistics;
import cn.lili.modules.system.entity.vo.Traces;
import cn.lili.modules.system.service.LogisticsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 售后业务层实现
 *
 * @author Chopper
 * @date 2020/11/17 7:38 下午
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class AfterSaleServiceImpl extends ServiceImpl<AfterSaleMapper, AfterSale> implements AfterSaleService {

    /**
     * 订单
     */
    @Autowired
    private OrderService orderService;
    /**
     * 订单货物
     */
    @Autowired
    private OrderItemService orderItemService;
    /**
     * 物流公司
     */
    @Autowired
    private LogisticsService logisticsService;
    /**
     * 店铺详情
     */
    @Autowired
    private StoreDetailService storeDetailService;
    /**
     * 售后支持，这里用于退款操作
     */
    @Autowired
    private RefundSupport refundSupport;
    /**
     * RocketMQ配置
     */
    @Autowired
    private RocketmqCustomProperties rocketmqCustomProperties;
    /**
     * RocketMQ
     */
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public IPage<AfterSaleVO> getAfterSalePages(AfterSaleSearchParams saleSearchParams) {
        return baseMapper.queryByParams(PageUtil.initPage(saleSearchParams), saleSearchParams.queryWrapper());
    }

    @Override
    public List<AfterSale> exportAfterSaleOrder(AfterSaleSearchParams saleSearchParams) {
        return this.list(saleSearchParams.queryWrapper());
    }

    @Override
    public AfterSaleVO getAfterSale(String sn) {
        return this.baseMapper.getAfterSaleVO(sn);
    }

    @Override
    public AfterSaleApplyVO getAfterSaleDTO(String sn) {

        AfterSaleApplyVO afterSaleApplyVO = new AfterSaleApplyVO();

        //获取订单货物判断是否可申请售后
        OrderItem orderItem = orderItemService.getBySn(sn);

        //未申请售后订单货物才能进行申请
        if (!orderItem.getAfterSaleStatus().equals(OrderItemAfterSaleStatusEnum.NOT_APPLIED.name())) {
            throw new ServiceException(ResultCode.AFTER_SALES_BAN);
        }

        //获取售后类型
        Order order = orderService.getBySn(orderItem.getOrderSn());

        //订单未支付，不能申请申请售后
        if (order.getPaymentMethod() == null) {
            throw new ServiceException(ResultCode.AFTER_SALES_NOT_PAY_ERROR);
        }
        //判断支付方式是否为线上支付
        if (order.getPaymentMethod().equals(PaymentMethodEnum.BANK_TRANSFER)) {
            afterSaleApplyVO.setRefundWay(AfterSaleRefundWayEnum.OFFLINE.name());
        } else {
            afterSaleApplyVO.setRefundWay(AfterSaleRefundWayEnum.ORIGINAL.name());
        }
        //判断订单类型，虚拟订单只支持退款
        if (order.getOrderType().equals(OrderTypeEnum.VIRTUAL.name())) {
            afterSaleApplyVO.setReturnMoney(true);
            afterSaleApplyVO.setReturnGoods(false);
        } else {
            afterSaleApplyVO.setReturnMoney(true);
            afterSaleApplyVO.setReturnGoods(true);
        }

        afterSaleApplyVO.setAccountType(order.getPaymentMethod());
        afterSaleApplyVO.setApplyRefundPrice(CurrencyUtil.sub(orderItem.getFlowPrice(), orderItem.getNum()));
        afterSaleApplyVO.setNum(orderItem.getNum());
        afterSaleApplyVO.setGoodsId(orderItem.getGoodsId());
        afterSaleApplyVO.setGoodsName(orderItem.getGoodsName());
        afterSaleApplyVO.setImage(orderItem.getImage());
        afterSaleApplyVO.setGoodsPrice(orderItem.getGoodsPrice());
        afterSaleApplyVO.setSkuId(orderItem.getSkuId());
        return afterSaleApplyVO;
    }

    @Override
    @AfterSaleLogPoint(sn = "#rvt.sn", description = "'售后申请:售后编号['+#rvt.sn+']'")
    @SystemLogPoint(description = "售后-售后申请", customerLog = "'售后申请:售后编号['+#rvt.sn+']'")
    public AfterSale saveAfterSale(AfterSaleDTO afterSaleDTO) {

        //检查当前订单是否可申请售后
        this.checkAfterSaleType(afterSaleDTO);

        //添加售后
        return addAfterSale(afterSaleDTO);
    }

    @AfterSaleLogPoint(sn = "#afterSaleSn", description = "'审核售后:售后编号['+#afterSaleSn+']，'+ #serviceStatus")
    @SystemLogPoint(description = "售后-审核售后", customerLog = "'审核售后:售后编号['+#afterSaleSn+']，'+ #serviceStatus")
    @Override
    public AfterSale review(String afterSaleSn, String serviceStatus, String remark, Double actualRefundPrice) {
        //根据售后单号获取售后单
        AfterSale afterSale = OperationalJudgment.judgment(this.getBySn(afterSaleSn));
        afterSale.setActualRefundPrice(actualRefundPrice);

        //判断为待审核的售后服务
        if (!afterSale.getServiceStatus().equals(AfterSaleStatusEnum.APPLY.name())) {
            throw new ServiceException(ResultCode.AFTER_SALES_DOUBLE_ERROR);
        }

        //判断审核状态
        //如果售后类型为：退款，审核状态为已通过并且退款方式为原路退回，售后单状态为已完成。
        //如果售后类型为：退款，审核状态已通过并且退款方式为线下退回，售后单状态为待退款。
        //如果售后类型不为退款，售后单状态为：已通过。
        AfterSaleStatusEnum afterSaleStatusEnum = null;
        if (serviceStatus.equals(AfterSaleStatusEnum.PASS.name())) {
            if (afterSale.getServiceType().equals(AfterSaleTypeEnum.RETURN_MONEY.name())) {
                if (afterSale.getRefundWay().equals(AfterSaleRefundWayEnum.ORIGINAL.name())) {
                    //如果为退款操作 && 在线支付 则直接进行退款
                    refundSupport.refund(afterSale);
                    afterSaleStatusEnum = AfterSaleStatusEnum.COMPLETE;
                } else {
                    afterSaleStatusEnum = AfterSaleStatusEnum.WAIT_REFUND;
                }
            } else {
                afterSaleStatusEnum = AfterSaleStatusEnum.PASS;
            }
        } else {
            afterSaleStatusEnum = AfterSaleStatusEnum.REFUSE;
        }
        afterSale.setServiceStatus(afterSaleStatusEnum.name());
        afterSale.setAuditRemark(remark);

        //根据售后编号修改售后单
        updateAfterSale(afterSaleSn, afterSale);

        //发送售后消息
        this.sendAfterSaleMessage(afterSale);

        return afterSale;
    }

    @AfterSaleLogPoint(sn = "#afterSaleSn", description = "'买家退货,物流填写:单号['+#afterSaleSn+']，物流单号为['+#logisticsNo+']'")
    @SystemLogPoint(description = "售后-买家退货,物流填写", customerLog = "'买家退货,物流填写:单号['+#afterSaleSn+']，物流单号为['+#logisticsNo+']'")
    @Override
    public AfterSale buyerDelivery(String afterSaleSn, String logisticsNo, String logisticsId, Date mDeliverTime) {

        //根据售后单号获取售后单
        AfterSale afterSale = OperationalJudgment.judgment(this.getBySn(afterSaleSn));

        //判断为已审核通过，待邮寄的售后服务
        if (!afterSale.getServiceStatus().equals(AfterSaleStatusEnum.PASS.name())) {
            throw new ServiceException(ResultCode.AFTER_STATUS_ERROR);
        }

        //查询会员回寄的物流公司信息
        Logistics logistics = logisticsService.getById(logisticsId);

        //判断物流公司是否为空
        if (logistics == null) {
            throw new ServiceException(ResultCode.AFTER_STATUS_ERROR);
        }

        afterSale.setMLogisticsCode(logistics.getId());
        afterSale.setMLogisticsName(logistics.getName());
        afterSale.setMLogisticsNo(logisticsNo);
        afterSale.setMDeliverTime(mDeliverTime);
        //修改售后单状态
        afterSale.setServiceStatus(AfterSaleStatusEnum.BUYER_RETURN.name());

        //根据售后编号修改售后单
        this.updateAfterSale(afterSaleSn, afterSale);
        return afterSale;
    }

    @Override
    public Traces deliveryTraces(String afterSaleSn) {

        //根据售后单号获取售后单
        AfterSale afterSale = OperationalJudgment.judgment(this.getBySn(afterSaleSn));

        return logisticsService.getLogistic(afterSale.getMLogisticsCode(), afterSale.getMLogisticsNo());
    }

    @Override
    @AfterSaleLogPoint(sn = "#afterSaleSn", description = "'售后-商家收货:单号['+#afterSaleSn+']，物流单号为['+#logisticsNo+']" +
            ",处理结果['+serviceStatus='PASS'?+'商家收货':'商家拒收'+']'")
    @SystemLogPoint(description = "售后-商家收货", customerLog = "'售后-商家收货:单号['+#afterSaleSn+']，物流单号为['+#logisticsNo+']" +
            ",处理结果['+serviceStatus='PASS'?+'商家收货':'商家拒收'+']'")
    public AfterSale storeConfirm(String afterSaleSn, String serviceStatus, String remark) {
        //根据售后单号获取售后单
        AfterSale afterSale = OperationalJudgment.judgment(this.getBySn(afterSaleSn));

        //判断是否为已邮寄售后单
        if (!afterSale.getServiceStatus().equals(AfterSaleStatusEnum.BUYER_RETURN.name())) {
            throw new ServiceException(ResultCode.AFTER_STATUS_ERROR);
        }
        AfterSaleStatusEnum afterSaleStatusEnum = null;
        String pass = "PASS";
        //判断审核状态
        //在线支付 则直接进行退款
        if (pass.equals(serviceStatus) &&
                afterSale.getRefundWay().equals(AfterSaleRefundWayEnum.ORIGINAL.name())) {
            refundSupport.refund(afterSale);
            afterSaleStatusEnum = AfterSaleStatusEnum.COMPLETE;
        } else if (pass.equals(serviceStatus)) {
            afterSaleStatusEnum = AfterSaleStatusEnum.WAIT_REFUND;
        } else {
            afterSaleStatusEnum = AfterSaleStatusEnum.SELLER_TERMINATION;

        }
        afterSale.setServiceStatus(afterSaleStatusEnum.name());
        afterSale.setAuditRemark(remark);

        //根据售后编号修改售后单
        this.updateAfterSale(afterSaleSn, afterSale);

        //发送售后消息
        this.sendAfterSaleMessage(afterSale);
        return afterSale;
    }

    @Override
    @AfterSaleLogPoint(sn = "#afterSaleSn", description = "'售后-平台退款:单号['+#afterSaleSn+']，备注为['+#remark+']'")
    @SystemLogPoint(description = "售后-平台退款", customerLog = "'售后-平台退款:单号['+#afterSaleSn+']，备注为['+#remark+']'")
    public AfterSale refund(String afterSaleSn, String remark) {
        //根据售后单号获取售后单
        AfterSale afterSale = OperationalJudgment.judgment(this.getBySn(afterSaleSn));
        afterSale.setServiceStatus(AfterSaleStatusEnum.COMPLETE.name());
        //根据售后编号修改售后单
        this.updateAfterSale(afterSaleSn, afterSale);
        //退款
        refundSupport.refund(afterSale);
        //发送退款消息
        this.sendAfterSaleMessage(afterSale);
        return afterSale;
    }

    @Override
    @AfterSaleLogPoint(sn = "#afterSaleSn", description = "'售后-买家确认解决:单号['+#afterSaleSn+']'")
    @SystemLogPoint(description = "售后-买家确认解决", customerLog = "'售后-买家确认解决:单号['+#afterSaleSn+']'")
    public AfterSale complete(String afterSaleSn) {
        AfterSale afterSale = this.getBySn(afterSaleSn);
        afterSale.setServiceStatus(AfterSaleStatusEnum.COMPLETE.name());
        this.updateAfterSale(afterSaleSn, afterSale);
        return afterSale;
    }

    @Override
    @AfterSaleLogPoint(sn = "#afterSaleSn", description = "'售后-买家取消:单号['+#afterSaleSn+']'")
    @SystemLogPoint(description = "售后-取消售后", customerLog = "'售后-买家取消:单号['+#afterSaleSn+']'")
    public AfterSale cancel(String afterSaleSn) {

        //根据售后单号获取售后单
        AfterSale afterSale = OperationalJudgment.judgment(this.getBySn(afterSaleSn));

        //判断售后单是否可以申请售后
        //如果售后状态为：待审核、已通过则可进行申请售后
        if (afterSale.getServiceStatus().equals(AfterSaleStatusEnum.APPLY.name())
                || afterSale.getServiceStatus().equals(AfterSaleStatusEnum.PASS.name())) {

            afterSale.setServiceStatus(AfterSaleStatusEnum.BUYER_CANCEL.name());

            //根据售后编号修改售后单
            this.updateAfterSale(afterSaleSn, afterSale);
            return afterSale;
        }
        throw new ServiceException(ResultCode.AFTER_SALES_CANCEL_ERROR);
    }

    @Override
    public Integer applyNum(String serviceType) {
        LambdaQueryWrapper<AfterSale> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(AfterSale::getServiceStatus, StoreStatusEnum.APPLYING.name());
        queryWrapper.eq(StringUtils.isNotEmpty(serviceType), AfterSale::getServiceType, serviceType);
        queryWrapper.eq(StringUtils.equals(UserContext.getCurrentUser().getRole().name(), UserEnums.STORE.name()),
                AfterSale::getStoreId, UserContext.getCurrentUser().getStoreId());
        return this.count(queryWrapper);
    }

    @Override
    public StoreAfterSaleAddressDTO getStoreAfterSaleAddressDTO(String sn) {
        return storeDetailService.getStoreAfterSaleAddressDTO(this.getBySn(sn).getStoreId());
    }

    @Override
    public IPage<AfterSale> getStatistics(StatisticsQueryParam statisticsQueryParam, PageVO pageVO) {

        LambdaQueryWrapper<AfterSale> queryWrapper = new LambdaQueryWrapper<>();
        Date[] dates = StatisticsDateUtil.getDateArray(statisticsQueryParam);
        queryWrapper.between(AfterSale::getCreateTime, dates[0], dates[1]);
        queryWrapper.eq(StringUtils.isNotEmpty(statisticsQueryParam.getStoreId()), AfterSale::getStoreId, statisticsQueryParam.getStoreId());

        return this.page(PageUtil.initPage(pageVO), queryWrapper);
    }

    /**
     * 创建售后
     *
     * @param afterSaleDTO 售后
     * @return 售后
     */
    private AfterSale addAfterSale(AfterSaleDTO afterSaleDTO) {
        //写入其他属性
        AuthUser tokenUser = UserContext.getCurrentUser();

        AfterSale afterSale = new AfterSale();
        BeanUtil.copyProperties(afterSaleDTO, afterSale);

        //写入会员信息
        afterSale.setMemberId(tokenUser.getId());
        afterSale.setMemberName(tokenUser.getNickName());

        //写入商家信息
        OrderItem orderItem = orderItemService.getBySn(afterSaleDTO.getOrderItemSn());
        Order order = orderService.getBySn(orderItem.getOrderSn());
        afterSale.setStoreId(order.getStoreId());
        afterSale.setStoreName(order.getStoreName());

        //写入订单商品信息
        afterSale.setGoodsImage(orderItem.getImage());
        afterSale.setGoodsName(orderItem.getGoodsName());
        afterSale.setSpecs(orderItem.getSpecs());
        afterSale.setFlowPrice(orderItem.getFlowPrice());

        //写入交易流水号
        afterSale.setTradeSn(order.getTradeSn());
        afterSale.setOrderSn(order.getSn());
        afterSale.setPayOrderNo(order.getPayOrderNo());
        afterSale.setOrderItemSn(orderItem.getSn());

        //写入状态
        afterSale.setServiceStatus(AfterSaleStatusEnum.APPLY.name());

        //TODO 退还积分

        //创建售后单号
        afterSale.setSn(SnowFlake.createStr("A"));

        //是否包含图片
        if (afterSaleDTO.getImages() != null) {
            afterSale.setAfterSaleImage(afterSaleDTO.getImages());
        }
        //计算退回金额
        afterSale.setApplyRefundPrice(CurrencyUtil.mul(orderItem.getUnitPrice(), afterSale.getNum()));
        //添加售后
        this.save(afterSale);
        //发送售后消息
        this.sendAfterSaleMessage(afterSale);
        //修改订单的售后状态
        orderItemService.updateAfterSaleStatus(orderItem.getSn(), OrderItemAfterSaleStatusEnum.ALREADY_APPLIED);
        return afterSale;
    }

    /**
     * 检查当前订单状态是否为可申请当前售后类型的状态
     *
     * @param afterSaleDTO 售后
     */
    private void checkAfterSaleType(AfterSaleDTO afterSaleDTO) {

        //判断数据是否为空
        if (null == afterSaleDTO || StringUtils.isEmpty(afterSaleDTO.getOrderItemSn())) {
            throw new ServiceException(ResultCode.ORDER_NOT_EXIST);
        }

        //获取订单货物判断是否可申请售后
        OrderItem orderItem = orderItemService.getBySn(afterSaleDTO.getOrderItemSn());

        //未申请售后订单货物才能进行申请
        if (!orderItem.getAfterSaleStatus().equals(OrderItemAfterSaleStatusEnum.NOT_APPLIED.name())) {
            throw new ServiceException(ResultCode.AFTER_SALES_BAN);
        }

        //获取售后类型
        Order order = orderService.getBySn(orderItem.getOrderSn());
        AfterSaleTypeEnum afterSaleTypeEnum = AfterSaleTypeEnum.valueOf(afterSaleDTO.getServiceType());
        switch (afterSaleTypeEnum) {
            case RETURN_MONEY:
                //只处理已付款的售后
                if (!PayStatusEnum.PAID.name().equals(order.getPayStatus())) {
                    throw new ServiceException(ResultCode.AFTER_SALES_BAN);
                }
                this.checkAfterSaleReturnMoneyParam(afterSaleDTO);
                break;
            case RETURN_GOODS:
                //是否为有效状态
                boolean availableStatus = StrUtil.equalsAny(order.getOrderStatus(), OrderStatusEnum.DELIVERED.name(), OrderStatusEnum.COMPLETED.name());
                if (!PayStatusEnum.PAID.name().equals(order.getPayStatus()) && availableStatus) {
                    throw new ServiceException(ResultCode.AFTER_SALES_BAN);
                }
                break;
            default:
                break;
        }

    }

    /**
     * 检测售后-退款参数
     *
     * @param afterSaleDTO
     */
    private void checkAfterSaleReturnMoneyParam(AfterSaleDTO afterSaleDTO) {
        //如果为线下支付银行信息不能为空
        if (AfterSaleRefundWayEnum.OFFLINE.name().equals(afterSaleDTO.getRefundWay())) {
            boolean emptyBankParam = StringUtils.isEmpty(afterSaleDTO.getBankDepositName())
                    || StringUtils.isEmpty(afterSaleDTO.getBankAccountName())
                    || StringUtils.isEmpty(afterSaleDTO.getBankAccountNumber());
            if (emptyBankParam) {
                throw new ServiceException(ResultCode.RETURN_MONEY_OFFLINE_BANK_ERROR);
            }

        }
    }

    /**
     * 根据sn获取信息
     *
     * @param sn 订单sn
     * @return 售后信息
     */
    private AfterSale getBySn(String sn) {
        QueryWrapper<AfterSale> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("sn", sn);
        return this.getOne(queryWrapper);
    }

    /**
     * 根据售后编号修改售后单
     *
     * @param afterSaleSn 售后单号
     * @param afterSale   售后单
     */
    private void updateAfterSale(String afterSaleSn, AfterSale afterSale) {
        LambdaUpdateWrapper<AfterSale> queryWrapper = Wrappers.lambdaUpdate();
        queryWrapper.eq(AfterSale::getSn, afterSaleSn);
        this.update(afterSale, queryWrapper);
    }

    /**
     * 发送售后消息
     *
     * @param afterSale 售后对象
     */
    private void sendAfterSaleMessage(AfterSale afterSale) {
        //发送售后创建消息
        String destination = rocketmqCustomProperties.getAfterSaleTopic() + ":" + AfterSaleTagsEnum.AFTER_SALE_STATUS_CHANGE.name();
        //发送订单变更mq消息
        rocketMQTemplate.asyncSend(destination, JSONUtil.toJsonStr(afterSale), RocketmqSendCallbackBuilder.commonCallback());
    }
}