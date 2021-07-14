package cn.lili.modules.order.order.serviceimpl;

import cn.lili.modules.order.order.service.AfterSaleLogService;
import cn.lili.modules.order.trade.entity.dos.AfterSaleLog;
import cn.lili.modules.order.trade.mapper.AfterSaleLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单日志业务层实现
 *
 * @author Chopper
 * @date 2020/11/17 7:37 下午
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class AfterSaleLogServiceImpl extends ServiceImpl<AfterSaleLogMapper, AfterSaleLog> implements AfterSaleLogService {

    @Override
    public List<AfterSaleLog> getAfterSaleLog(String sn) {
        QueryWrapper queryWrapper = Wrappers.query();
        queryWrapper.eq("sn", sn);
        return this.list(queryWrapper);
    }
}