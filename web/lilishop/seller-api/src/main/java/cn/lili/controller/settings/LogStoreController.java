package cn.lili.controller.settings;

import cn.lili.common.security.context.UserContext;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.vo.PageVO;
import cn.lili.common.vo.ResultMessage;
import cn.lili.common.vo.SearchVO;
import cn.lili.modules.permission.service.SystemLogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * 店铺端,日志管理接口
 *
 * @author Chopper
 * @date: 2020/11/22 14:23
 */
@RestController
@Transactional(rollbackFor = Exception.class)
@Api(tags = "店铺端,日志管理接口")
@RequestMapping("/store/log")
public class LogStoreController {
    @Autowired
    private SystemLogService systemLogService;

    @GetMapping(value = "/getAllByPage")
    @ApiOperation(value = "分页获取全部")
    public ResultMessage<Object> getAllByPage(@RequestParam(required = false) Integer type,
                                              @RequestParam String key,
                                              String operatorName,
                                              SearchVO searchVo,
                                              PageVO pageVo) {
        return ResultUtil.data(systemLogService.queryLog(UserContext.getCurrentUser().getStoreId(), operatorName, key, searchVo, pageVo));
    }
}
