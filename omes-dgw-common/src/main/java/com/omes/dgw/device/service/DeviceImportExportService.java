package com.omes.dgw.device.service;

import com.omes.dgw.device.dto.DeviceImportResultVo;
import com.omes.dgw.device.dto.DeviceQuery;
import org.springframework.web.multipart.MultipartFile;

public interface DeviceImportExportService {

    byte[] exportExcel(DeviceQuery query);

    byte[] downloadTemplate();

    DeviceImportResultVo importExcel(MultipartFile file);
}
