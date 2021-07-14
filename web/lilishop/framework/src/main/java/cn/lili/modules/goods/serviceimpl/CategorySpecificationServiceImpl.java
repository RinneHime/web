package cn.lili.modules.goods.serviceimpl;

import cn.lili.modules.goods.entity.dos.CategorySpecification;
import cn.lili.modules.goods.entity.dos.Specification;
import cn.lili.modules.goods.mapper.CategorySpecificationMapper;
import cn.lili.modules.goods.service.CategorySpecificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品分类规格业务层实现
 *
 * @author pikachu
 * @date 2020-02-23 15:18:56
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class CategorySpecificationServiceImpl extends ServiceImpl<CategorySpecificationMapper, CategorySpecification> implements CategorySpecificationService {

    @Override
    public List<Specification> getCategorySpecList(String categoryId) {
        return this.baseMapper.getCategorySpecList(categoryId);
    }

    @Override
    public void deleteByCategoryId(String categoryId) {
        this.baseMapper.delete(new LambdaQueryWrapper<CategorySpecification>().eq(CategorySpecification::getCategoryId, categoryId));
    }
}