package com.omes.dgw.modscan.service;

import com.omes.dgw.modscan.dto.ModscanRegisterAreaOptionVo;
import com.omes.dgw.modscan.dto.ModscanScanRequest;
import com.omes.dgw.modscan.dto.ModscanScanVo;
import com.omes.dgw.modscan.dto.ModscanSessionCloseRequest;
import com.omes.dgw.modscan.dto.ModscanSessionOpenRequest;
import com.omes.dgw.modscan.dto.ModscanSessionVo;

import java.util.List;

public interface ModscanService {

    ModscanSessionVo openSession(ModscanSessionOpenRequest request);

    ModscanScanVo scan(ModscanScanRequest request);

    void closeSession(ModscanSessionCloseRequest request);

    List<ModscanRegisterAreaOptionVo> listRegisterAreas();
}
