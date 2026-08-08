package site.bnk.gen;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import jspeed.websvc.Action;

import com.itplus.common.actions.common.util.ArrayHelper;
/** 
******************************************************************************************************************
* PROJ : 경남은행 메타마이너 업그레이드
* NAME : INFOTYPEAutoGeneratorAction
* DESC : 인포타입 자동 생성을 한다.
* AUTHOR : 박정섭
* VER : 1.0
* Copyright 2024 GTOne Co.,LTD.  All rights reserved 
******************************************************************************************************************
* No     DATE           Author                   Description 
****************************************************************************************************************** 
* 1.0   2024. 07. 18.   박정섭                   최초 Release
******************************************************************************************************************
*/
public class InfotypeAutoGeneratorAction extends Action{
	/** ABBR을 인포타입명에 포함할 사전 ID. 운영 환경에 따라 변경할 수 있다. */
	public String ABBR_INFOTYPE_DIC_ID = "2c9c9081/9e817a83/019e/81871aae/0074";

	/** ABBR을 포함할 대상 사전 ID를 변경한다. */
	public void setAbbrInfotypeDicId(String dictionaryId) {
		this.ABBR_INFOTYPE_DIC_ID = dictionaryId;
	}

	@Override
	public HashMap execute(HashMap in) throws Exception {
		HashMap outputHash = new LinkedHashMap();
		
		try {
			HashMap[] result = null;
			
			ArrayHelper ah = new ArrayHelper();
			Object colDomNm = in.get("COL_DOM_NM");
			String infotypeNm = null;	// 인포타입명
			
			
			System.out.println("**********************************************************************");
	        Iterator<String> iterator = in.keySet().iterator();
	        while (iterator.hasNext()) {
	           String Key = iterator.next();
	           System.out.println("key = " +Key);
	           System.out.println("value = " +in.get(Key));
	        }      
	        System.out.println("**********************************************************************");
	        
	        /*
	         **********************************************************************
	         2024 08 27 DOM_GRP_NM 값이 없어서 CR등록 
				key = DFLT_SCAL
				value = [[8]]
				key = DOMAIN_LGCL_NM
				value = [[]]
				key = DFLT_PRCS
				value = [[]]
				key = LINE
				value = [[0]]
				key = COL_DOM_NM
				value = [[년도]]
				key = ABBR
				value = [[VC]]
				key = DBMS_DATATYPE
				value = [[VARCHAR]]
				**********************************************************************
	         * */
			
			if(ah.isArray(colDomNm)){
				Object[] domGrpNmArray = ah.fetchArrayOfArray(in.get("DOM_GRP_NM"));		// 도메인 그룹명
				Object[] colDomNmArray = ah.fetchArrayOfArray(colDomNm);					// 도메인 명
				Object[] dbmsDatatypeArray = ah.fetchArrayOfArray(in.get("DBMS_DATATYPE"));	// DBMS 데이터타입
				Object[] abbrArray = ah.fetchArrayOfArray(in.get("ABBR"));					// DBMS 데이터타입 약어명
				Object[] dfltScalArray = ah.fetchArrayOfArray(in.get("DFLT_SCAL"));			// 기본 자리수
				Object[] dfltPrcsArray = ah.fetchArrayOfArray(in.get("DFLT_PRCS"));			// 기본 소수점
				Object[] lineArray = ah.fetchArrayOfArray(in.get("LINE"));					// 순서
				Object[] ufwDicIdArray = ah.fetchArrayOfArray(in.get("UFW_DIC_ID"));				// 사전 ID
				
				result = new HashMap[colDomNmArray.length];
			
				for(int i = 0; i < colDomNmArray.length; i++){
					System.out.println("INFOTYPEAutoGeneratorAction.start ==> " + i);
					result[i] = new HashMap();
					String domGrp = (String) domGrpNmArray[i];
					String domNm = (String) colDomNmArray[i];
					String ufwDicId = ufwDicIdArray[i] == null ? "" : String.valueOf(ufwDicIdArray[i]).trim();
					System.out.println("domGrp >>> " +domGrp);
					System.out.println("domNm >>> "+domNm);
					
					// 사전 ID에 따라 ABBR 포함 여부를 분기한다.
					if (ABBR_INFOTYPE_DIC_ID != null && ABBR_INFOTYPE_DIC_ID.equals(ufwDicId)) {
						// 대상 사전: COL_DOM_NM + ABBR + DFLT_SCAL + 선택적 소수점
						infotypeNm = buildScaleInfotypeName(domNm, abbrArray[i], dfltScalArray[i], dfltPrcsArray[i], true);
					} else {
						// 그 외 사전: ABBR 제외, COL_DOM_NM + DFLT_SCAL + 선택적 소수점
						infotypeNm = buildScaleInfotypeName(domNm, null, dfltScalArray[i], dfltPrcsArray[i], false);
					}
					System.out.println("☆★☆★☆★☆★☆★  Auto-Generated Infotype Name : "+infotypeNm+"  ☆★☆★☆★☆★☆★");
					result[i].put("INFOTYPE_NM", infotypeNm);	// 인포타입명
					result[i].put("INFOTYPE_LGCL_NM", infotypeNm);	// 인포타입 논리명
					result[i].put("LINE", lineArray[i]);
				}
			}
			
			this.genOutputHash(outputHash, result, "RETURN");
			
		} catch(Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
		}
		
		return outputHash;
	}

	/** ABBR 포함 여부에 따라 인포타입명을 생성한다. */
	private String buildScaleInfotypeName(String domainName, Object abbreviation, Object scale,
			Object precision, boolean includeAbbreviation) {
		StringBuilder name = new StringBuilder();
		name.append(domainName == null ? "" : domainName);
		if (includeAbbreviation) {
			name.append(abbreviation == null ? "" : String.valueOf(abbreviation));
		}
		name.append(scale == null ? "" : String.valueOf(scale));
		if (precision != null && String.valueOf(precision).trim().length() > 0) {
			name.append(",").append(String.valueOf(precision).trim());
		}
		return name.toString();
	}

	@Override
	public String getCodeValue(String arg0, String arg1) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
