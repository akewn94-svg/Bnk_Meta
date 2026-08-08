package site.bnk.check;

import java.util.HashMap;
import java.util.Iterator;
import java.lang.reflect.Array;

import com.itplus.mm.server.datadic.AbstractValidationCheck;
/** 
*************************************************************************************************************************** 
* PROJ : 경남은행 메타마이너 업그레이드
* NAME : UtwValidationCheck.java 
* DESC : 단어 유효성 검사 클래스
* AUTHOR : 박정섭
* VER : 1.0 
* Copyright 2024 GTOne Co.,LTD.  All rights reserved 
*************************************************************************************************************************** 
* No     DATE           Author                   Description 
*************************************************************************************************************************** 
* 1.0   2024. 07. 23.   박정섭                   최초 Release
***************************************************************************************************************************
*/
public class UtwValidationCheck extends AbstractValidationCheck{
	/** 단어사전 A에 적용할 검증 대상 ID. */
	private static final String UTW_DIC_ID_A = "2c9c9081/9e817a83/019e/8187033e/0072";

	/**
	 * <pre>
	 * 표준에 대한 사용자 정의 유효성 체크를 한다.
	 * </pre>
	 * @param info
	 * 
	* [단어]
	 * REQ_TP_CD		String		생성(WFGB_I), 수정(WFGB_U), 삭제(WFGB_D)
	 * REQ_EDIT_TYPE	String		생성(), 수정(UPDATE), 삭제(DELETE)
	 * UTW_DIC_ID		String		단어사전ID
	 * UTW_ID			String		단어 ID
	 * DAT_STRC_ID		String		데이터 구조 ID
	 * UTW_NM			String		한글 명
	 * ABBR				String		약어
	 * EN_NM			String		영문 명
	 * UTW_TP_CD		String		단어 유형 코드
	 * UTW_DEF			String		정의 
	 **/
	public HashMap checkInsertValid(HashMap info) throws Exception {		
		HashMap result = new HashMap();
		String succYn = "Y";
		String errMsg = "";
		
		try{

			System.out.println("**********************************************************************");
		    System.out.println("                      checkUtwInsertValid");
		    System.out.println("**********************************************************************");

			System.out.println("**********************************************************************");
	        Iterator<String> iterator = info.keySet().iterator();
	        while (iterator.hasNext()) {
	        	String Key = iterator.next();
	            System.out.println("key = " +Key);
	            System.out.println("value = " +info.get(Key));
	        }      
	        System.out.println("**********************************************************************");
			
	        /** 정의 중복 여부를 먼저 검사하고, 오류가 없을 때 다음 검사를 진행한다. */
	        if (errMsg.length() > 0) {
	        } else {
	        	errMsg = validateDefinition(info);
	        	if (errMsg.length() > 0) {
	        		succYn = "N";
	        	}
	        }

	        /** 단어사전 A인 경우 한글명/영문명 형식을 검사한다. */
	        if (errMsg.length() > 0) {
	        } else if (UTW_DIC_ID_A.equalsIgnoreCase(extractFirstString(info.get("UTW_DIC_ID")))) {
	        	errMsg = validateDictionaryA(info);
	        	if (errMsg.length() > 0) {
	        		succYn = "N";
	        	}
	        }

			result.put("VALID_ID", null);
			result.put("ERROR_ID", null);
			result.put("ERROR_MSG", errMsg);
			result.put("VALD_CHECK_SUCC_YN", succYn);
		}catch(Exception e){
			e.printStackTrace();
			throw e;
		}
		
		return result;
	}
	
	public HashMap checkUpdateValid(HashMap info) throws Exception {	
		HashMap result = new HashMap();
		String succYn = "Y";
		String errMsg = "";
		
		try{
			System.out.println("**********************************************************************");
		    System.out.println("                      checkUtwUpdateValid");
		    System.out.println("**********************************************************************");
			

			System.out.println("**********************************************************************");
	        Iterator<String> iterator = info.keySet().iterator();
	        while (iterator.hasNext()) {
	        	String Key = iterator.next();
	            System.out.println("key = " +Key);
	            System.out.println("value = " +info.get(Key));
	        }      
	        System.out.println("**********************************************************************");
			
	        if (errMsg.length() > 0) {
	        } else {
	        	errMsg = validateDefinition(info);
	        	if (errMsg.length() > 0) {
	        		succYn = "N";
	        	}
	        }

	        if (errMsg.length() > 0) {
	        } else if (UTW_DIC_ID_A.equalsIgnoreCase(extractFirstString(info.get("UTW_DIC_ID")))) {
	        	errMsg = validateDictionaryA(info);
	        	if (errMsg.length() > 0) {
	        		succYn = "N";
	        	}
	        }

			result.put("VALID_ID", null);
			result.put("ERROR_ID", null);
			result.put("ERROR_MSG", errMsg);
			result.put("VALD_CHECK_SUCC_YN", succYn);
		}catch(Exception e){
			e.printStackTrace();
			throw e;
		}
		
		return result;
	}
	
	public HashMap checkDeleteValid(HashMap info) throws Exception {		
		HashMap result = new HashMap();
		String succYn = "Y";
		String errMsg = "";
		
		try{
			
			result.put("VALID_ID", null);
			result.put("ERROR_ID", null);
			result.put("ERROR_MSG", errMsg);
			result.put("VALD_CHECK_SUCC_YN", succYn);
		}catch(Exception e){
			e.printStackTrace();
			throw e;
		}
		
		return result;
	}	

	/** 사전 A의 한글명/영문명 형식 규칙을 검사한다. */
	private String validateDictionaryA(HashMap info) {
		String utwNm = extractFirstString(info.get("UTW_NM"));
		String enNm = extractFirstString(info.get("EN_NM"));

		// 명사형 여부는 언어 사전이나 형태소 분석기 없이는 형식만으로 판별할 수 없다.
		// 따라서 현재는 값 존재 여부와 허용 문자 규칙을 검사한다.
		if (utwNm.length() == 0) {
			return "한글명을 입력해야 합니다.";
		}
		if (utwNm.matches(".*\\s.*")) {
			return "한글명에는 띄어쓰기를 사용할 수 없습니다.";
		}
		if (utwNm.matches("[0-9]+")) {
			return "숫자만으로 한글명을 등록할 수 없습니다.";
		}
		if (!utwNm.matches("[가-힣A-Za-z0-9]+")) {
			return "한글명에는 한글, 영문, 숫자만 사용할 수 있습니다.";
		}

		if (enNm.length() == 0) {
			return "영문명을 입력해야 합니다.";
		}
		// 영문명을 공백으로 나누고 숫자 토큰은 제외한 뒤 영문 단어만 검사한다.
		// 예: "1 Month"는 허용하지만 "1 MONTH", "1 month"는 허용하지 않는다.
		String[] enWords = enNm.split(" ", -1);
		for (String enWord : enWords) {
			if (enWord.matches("[0-9]+")) {
				continue;
			}
			if (!enWord.matches("[A-Z][a-z]*")) {
				return "영문명은 각 영문 단어의 첫 글자만 대문자로 입력해야 합니다.";
			}
		}
		return "";
	}

	/**
	 * 정의가 단어명/영문명/약어를 그대로 반복 형태인지 검사한다.
	 * 예: UTW_NM이 "고객명"이면 UTW_DEF의 "고객명"은 허용하지 않는다.
	 */
	private String validateDefinition(HashMap info) {
		String definition = extractFirstString(info.get("UTW_DEF"));
		if (definition.length() == 0) {
			return "";
		}

		String trimmedDefinition = definition.trim().replaceAll("\\s+", " ");
		String koreanName = extractFirstString(info.get("UTW_NM")).trim();
		String englishName = extractFirstString(info.get("EN_NM")).trim();
		String abbreviation = extractFirstString(info.get("ABBR")).trim();

		if (trimmedDefinition.equalsIgnoreCase(koreanName)
				|| trimmedDefinition.equalsIgnoreCase(englishName)
				|| trimmedDefinition.equalsIgnoreCase(abbreviation)) {
			return "단어 정의는 단어명, 영문명, 약어와 같을 수 없습니다.";
		}
		return "";
	}

	/** [[값]], 배열, 컬렉션 형태의 프레임워크 입력에서 첫 번째 실제 문자열을 추출한다. */
	private String extractFirstString(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof CharSequence) {
			return value.toString().trim();
		}
		if (value.getClass().isArray()) {
			return Array.getLength(value) == 0 ? "" : extractFirstString(Array.get(value, 0));
		}
		if (value instanceof Iterable<?>) {
			java.util.Iterator<?> iterator = ((Iterable<?>) value).iterator();
			return iterator.hasNext() ? extractFirstString(iterator.next()) : "";
		}
		return String.valueOf(value).trim();
	}
	
}
