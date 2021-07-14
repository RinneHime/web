package cn.lili.modules.goods.serviceimpl;

import cn.lili.modules.goods.entity.dos.CategoryParameterGroup;
import cn.lili.modules.goods.entity.dos.Parameters;
import cn.lili.modules.goods.entity.vos.ParameterGroupVO;
import cn.lili.modules.goods.mapper.CategoryParameterGroupMapper;
import cn.lili.modules.goods.service.CategoryParameterGroupService;
import cn.lili.modules.goods.service.ParametersService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类绑定参数组接口实现
 *
 * @author pikachu
 * @version v1.0
 * @since v1.0
 * 2020-03-02 16:45:03
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class CategoryParameterGroupServiceImpl extends ServiceImpl<CategoryParameterGroupMapper, CategoryParameterGroup> implements CategoryParameterGroupService {
    /**
     * 商品参数
     */
    @Autowired
    private ParametersService parametersService;

    @Override
    public List<ParameterGroupVO> getCategoryParams(String categoryId) {
        //根据id查询参数组
        List<CategoryParameterGroup> groups = this.getCategoryGroup(categoryId);
        //查询参数
        List<Parameters> params = parametersService.list(new QueryWrapper<Parameters>().eq("category_id", categoryId));
        //组合参数vo
        return convertParamList(groups, params);
    }

    @Override
    public List<CategoryParameterGroup> getCategoryGroup(String categoryId) {
        return this.list(new QueryWrapper<CategoryParameterGroup>().eq("category_id", categoryId));
    }

    /**
     * 拼装参数组和参数的返回值
     *
     * @param groupList 参数组list
     * @param paramList 商品参数list
     * @return
     */
    public List<ParameterGroupVO> convertParamList(List<CategoryParameterGroup> groupList, List<Parameters> paramList) {
        Map<String, List<Parameters>> map = new HashMap<>(paramList.size());
        for (Parameters param : paramList) {
            List<Parameters> list = map.get(param.getGroupId());
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(param);
            map.put(param.getGroupId(), list);
        }
        List<ParameterGroupVO> resList = new ArrayList<>();
        for (CategoryParameterGroup group : groupList) {
            ParameterGroupVO groupVo = new ParameterGroupVO();
            groupVo.setGroupId(group.getId());
            groupVo.setGroupName(group.getGroupName());
            groupVo.setParams(map.get(group.getId()) == null ? new ArrayList<>() : map.get(group.getId()));
            resList.add(groupVo);
        }
        return resList;
    }
}