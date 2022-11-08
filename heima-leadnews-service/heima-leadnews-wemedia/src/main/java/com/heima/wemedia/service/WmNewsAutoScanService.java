package com.heima.wemedia.service;

import com.heima.model.wemedia.pojos.WmNews;

public interface WmNewsAutoScanService {
    /**
     * 自媒体文章审核
     */
    public void autoScanWmNews(WmNews wmNew);
}
