package com.heima.wemedia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.file.service.FileStorageService;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmMaterialDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.service.WmMaterialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;


@Slf4j
@Service
@Transactional
public class WmMaterialServiceImpl extends ServiceImpl<WmMaterialMapper, WmMaterial> implements WmMaterialService {

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 上传图片
     *
     * @param multipartFile
     * @return
     */
    @Override
    public ResponseResult uploadPicture(MultipartFile multipartFile) {
        //检查参数
        if (multipartFile == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        if (WmThreadLocalUtil.getWmUser() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //上传图片(minIO)
        UUID uuid = UUID.randomUUID();
        String fileName = uuid.toString().replace("-", "");
        String originalFileName = multipartFile.getOriginalFilename();
        assert originalFileName != null;
        String suffix = originalFileName.substring(originalFileName.lastIndexOf("."));

        String filePath = null;
        try {
            filePath = fileStorageService.uploadImgFile("", fileName + suffix, multipartFile.getInputStream());
            log.info("上传图片成功,filePath:{}", filePath);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("上传图片失败,原因:{}", e.getMessage());
        }

        //保存图片信息
        WmMaterial wmMaterial = new WmMaterial();
        wmMaterial.setUrl(fileName + suffix);
        wmMaterial.setUserId(WmThreadLocalUtil.getWmUser().getId());
        wmMaterial.setUrl(filePath);
        wmMaterial.setIsCollection((short) 0);
        wmMaterial.setType((short) 0);
        wmMaterial.setCreatedTime(new Date());

        save(wmMaterial);

        return ResponseResult.okResult(wmMaterial);
    }

    /**
     * 素材列表
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult findList(WmMaterialDto dto) {

        //检查参数
        dto.checkParam();

        //分页查询
        IPage<WmMaterial> page = new Page<>(dto.getPage(), dto.getSize());

        LambdaQueryWrapper<WmMaterial> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WmMaterial::getUserId, WmThreadLocalUtil.getWmUser().getId());

        //判断是否需要查询收藏的素材
        if (dto.getIsCollection() != null && dto.getIsCollection() == 1) {
            queryWrapper.eq(WmMaterial::getIsCollection, dto.getIsCollection());
        }

        //根据时间排序
        queryWrapper.orderByDesc(WmMaterial::getCreatedTime);

        IPage<WmMaterial> page1 = page(page, queryWrapper);

        PageResponseResult pageResponseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page1.getTotal());
        pageResponseResult.setData(page1.getRecords());

        return pageResponseResult;
    }
}
