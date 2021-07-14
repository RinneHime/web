package cn.lili.controller.promotion;

import cn.lili.common.enums.ResultUtil;
import cn.lili.common.security.AuthUser;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.vo.PageVO;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.promotion.entity.dos.Seckill;
import cn.lili.modules.promotion.entity.dos.SeckillApply;
import cn.lili.modules.promotion.entity.vos.SeckillSearchParams;
import cn.lili.modules.promotion.entity.vos.SeckillVO;
import cn.lili.modules.promotion.service.SeckillApplyService;
import cn.lili.modules.promotion.service.SeckillService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端,秒杀活动接口
 *
 * @author paulG
 * @date 2020/8/20
 **/
@RestController
@Api(tags = "管理端,秒杀活动接口")
@RequestMapping("/manager/promotion/seckill")
public class SeckillManagerController {
    @Autowired
    private SeckillService seckillService;
    @Autowired
    private SeckillApplyService seckillApplyService;


    @ApiOperation(value = "初始化秒杀活动(初始化方法，默认初始化30天内的活动）")
    @GetMapping("/init")
    public void addSeckill() {
        seckillService.init();
    }


    @ApiOperation(value = "修改秒杀活动")
    @PutMapping
    public ResultMessage<Seckill> updateSeckill(SeckillVO seckillVO) {
        AuthUser currentUser = UserContext.getCurrentUser();
        seckillVO.setStoreId(currentUser.getId());
        seckillVO.setStoreName(currentUser.getUsername());
        seckillService.modifySeckill(seckillVO);
        return ResultUtil.data(seckillVO);
    }

    @ApiOperation(value = "通过id获取")
    @ApiImplicitParam(name = "id", value = "秒杀活动ID", required = true, dataType = "String", paramType = "path")
    @GetMapping(value = "/{id}")
    public ResultMessage<Seckill> get(@PathVariable String id) {
        Seckill seckill = seckillService.getById(id);
        return ResultUtil.data(seckill);
    }

    @ApiOperation(value = "分页查询秒杀活动列表")
    @GetMapping
    public ResultMessage<IPage<Seckill>> getAll(SeckillSearchParams param, PageVO pageVo) {
        return ResultUtil.data(seckillService.getSeckillByPageFromMysql(param, pageVo));
    }

    @ApiOperation(value = "删除一个秒杀活动")
    @ApiImplicitParam(name = "id", value = "秒杀活动ID", required = true, dataType = "String", paramType = "path")
    @DeleteMapping("/{id}")
    public ResultMessage<Object> deleteSeckill(@PathVariable String id) {
        seckillService.deleteSeckill(id);
        return ResultUtil.success();
    }

    @ApiOperation(value = "关闭一个秒杀活动")
    @ApiImplicitParam(name = "id", value = "秒杀活动ID", required = true, dataType = "String", paramType = "path")
    @PutMapping("/close/{id}")
    public ResultMessage<Object> closeSeckill(@PathVariable String id) {
        seckillService.closeSeckill(id);
        return ResultUtil.success();
    }

    @ApiOperation(value = "开启一个秒杀活动")
    @ApiImplicitParam(name = "id", value = "秒杀活动ID", required = true, dataType = "String", paramType = "path")
    @PutMapping("/open/{id}")
    public ResultMessage<Object> openSeckill(@PathVariable String id) {
        seckillService.openSeckill(id);
        return ResultUtil.success();
    }

    @ApiOperation(value = "获取秒杀活动申请列表")
    @GetMapping("/apply")
    public ResultMessage<IPage<SeckillApply>> getSeckillApply(SeckillSearchParams param, PageVO pageVo) {
        IPage<SeckillApply> seckillApply = seckillApplyService.getSeckillApplyFromMongo(param, pageVo);
        return ResultUtil.data(seckillApply);
    }

}
