package cn.lili.controller.member;


import cn.lili.common.enums.ResultUtil;
import cn.lili.common.vo.PageVO;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.member.entity.dos.MemberWithdrawApply;
import cn.lili.modules.member.entity.vo.MemberWithdrawApplyQueryVO;
import cn.lili.modules.member.service.MemberWithdrawApplyService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 管理端,余额提现记录接口
 *
 * @author pikachu
 * @date: 2020/11/16 10:07 下午
 */
@RestController
@Api(tags = "管理端,余额提现记录接口")
@RequestMapping("/manager/members/withdraw-apply")
@Transactional(rollbackFor = Exception.class)
public class MemberWithdrawApplyManagerController {
    @Autowired
    private MemberWithdrawApplyService memberWithdrawApplyService;


    @ApiOperation(value = "分页获取提现记录")
    @GetMapping
    public ResultMessage<IPage<MemberWithdrawApply>> getByPage(PageVO page, MemberWithdrawApplyQueryVO memberWithdrawApplyQueryVO) {
        //构建查询 返回数据
        IPage<MemberWithdrawApply> memberWithdrawApplyPage = memberWithdrawApplyService.getMemberWithdrawPage(page, memberWithdrawApplyQueryVO);
        return ResultUtil.data(memberWithdrawApplyPage);
    }


    @ApiOperation(value = "提现申请审核")
    @PostMapping
    @ApiImplicitParams({
            @ApiImplicitParam(name = "applyId", value = "审核记录id", required = true, paramType = "query"),
            @ApiImplicitParam(name = "result", value = "审核结果", required = true, paramType = "query", dataType = "boolean"),
            @ApiImplicitParam(name = "remark", value = "审核备注", paramType = "query")
    })
    public Boolean audit(String applyId, Boolean result, String remark) {
        return memberWithdrawApplyService.audit(applyId, result, remark);
    }

}
