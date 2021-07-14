package cn.lili.modules.payment.kit;

import cn.lili.common.utils.SnowFlake;
import cn.lili.common.utils.SpringContextUtil;
import cn.lili.modules.order.order.entity.dos.AfterSale;
import cn.lili.modules.order.order.entity.dos.Order;
import cn.lili.modules.order.order.service.AfterSaleService;
import cn.lili.modules.order.order.service.OrderService;
import cn.lili.modules.order.order.service.StoreFlowService;
import cn.lili.modules.payment.entity.RefundLog;
import cn.lili.modules.payment.kit.enums.PaymentMethodEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

/**
 * 退款支持
 *
 * @author Chopper
 * @date 2020-12-19 09:25
 */
@Component
@Slf4j
public class RefundSupport {
    /**
     * 店铺流水
     */
    @Autowired
    private StoreFlowService storeFlowService;
    /**
     * 售后
     */
    @Autowired
    private AfterSaleService afterSaleService;
    /**
     * 订单
     */
    @Autowired
    private OrderService orderService;

    /**
     * 售后退款
     *
     * @param afterSale
     */
    public void refund(AfterSale afterSale) {
        Order order = orderService.getBySn(afterSale.getOrderSn());
        RefundLog refundLog = RefundLog.builder()
                .isRefund(false)
                .totalAmount(afterSale.getActualRefundPrice())
                .payPrice(afterSale.getActualRefundPrice())
                .memberId(afterSale.getMemberId())
                .paymentName(order.getPaymentMethod())
                .afterSaleNo(afterSale.getSn())
                .paymentReceivableNo(order.getReceivableNo())
                .outOrderNo("AF" + SnowFlake.getIdStr())
                .orderSn(afterSale.getOrderSn())
                .refundReason(afterSale.getReason())
                .build();
        PaymentMethodEnum paymentMethodEnum = PaymentMethodEnum.paymentNameOf(order.getPaymentMethod());
        Payment payment = (Payment) SpringContextUtil.getBean(paymentMethodEnum.getPlugin());
        payment.refund(refundLog);

        //记录售后单中的退款金额
        afterSaleService.update(new LambdaUpdateWrapper<AfterSale>()
                .eq(AfterSale::getId, afterSale.getId())
                .set(AfterSale::getRefundTime, new Date()));
        //记录退款流水
        storeFlowService.refundOrder(afterSale);
    }

    /**
     * 订单取消
     *
     * @param afterSale
     */
    public void cancel(AfterSale afterSale) {

        Order order = orderService.getBySn(afterSale.getOrderSn());
        RefundLog refundLog = RefundLog.builder()
                .isRefund(false)
                .totalAmount(afterSale.getActualRefundPrice())
                .payPrice(afterSale.getActualRefundPrice())
                .memberId(afterSale.getMemberId())
                .paymentName(order.getPaymentMethod())
                .afterSaleNo(afterSale.getSn())
                .paymentReceivableNo(order.getReceivableNo())
                .outOrderNo("AF" + SnowFlake.getIdStr())
                .orderSn(afterSale.getOrderSn())
                .refundReason(afterSale.getReason())
                .build();
        PaymentMethodEnum paymentMethodEnum = PaymentMethodEnum.paymentNameOf(order.getPaymentMethod());
        Payment payment = (Payment) SpringContextUtil.getBean(paymentMethodEnum.getPlugin());
        payment.refund(refundLog);
    }


    /**
     * 退款通知
     *
     * @param paymentMethodEnum 支付渠道
     */
    public void notify(PaymentMethodEnum paymentMethodEnum,
                       HttpServletRequest request) {

        //获取支付插件
        Payment payment = (Payment) SpringContextUtil.getBean(paymentMethodEnum.getPlugin());
        payment.refundNotify(request);
    }

}
