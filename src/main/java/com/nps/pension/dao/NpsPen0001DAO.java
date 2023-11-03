package com.nps.pension.dao;

import com.nps.pension.dto.NpsPen0001DTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.HashMap;
import java.util.List;

@Mapper
public interface NpsPen0001DAO {
  
  public List<HashMap<String, Object>> selectNpsPen0001();
  public String selectCallbackUrl(NpsPen0001DTO dto);
  public void insertNpsPen0001(NpsPen0001DTO dto);
  
}
