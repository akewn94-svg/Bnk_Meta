package site.bnk.gen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.lang.reflect.Array;
import java.util.Locale;

import com.gtone.mm.common.dao.JFDao;
import com.gtone.mm.core.handler.dao.DefaultAbbrGenDao;
import com.gtone.mm.support.ApplicationContextProvider;
import com.gtone.mm.util.DBUtil;
import com.gtone.mm.util.DataUtil;
import com.gtone.mm.util.EnvironmentUtil;
import com.itplus.mm.actions.MMException;
import com.itplus.mm.server.datadic.IUtwAbbrAutoGen;


/**
 * 영문명(EN_NM)을 기준으로 용어 약어(ABBR)를 자동 생성한다.
 * 처리 순서는 사전 옵션/기존 약어 조회, 공백 기준 단어 분리,
 * 불필요한 단어 제거, 자음·숫자 추출, 길이 배분 및 중복 회피이다.
 */
public class AbbrAutoGeneratorAction implements IUtwAbbrAutoGen{
	/** 약어에서 제외하는 관사·전치사 등의 목록. */
	private List<String> invalidWords = new ArrayList<>();
	/** 이미 등록되어 있어 중복을 피해야 하는 약어 목록. */
	private List<String> registeredWords = new ArrayList<>();
	/** 단어별 숫자를 보관하기 위한 목록(현재 생성 과정에서는 지역 목록을 사용한다). */
	private List<String> numberWords = new ArrayList<>();
	/** 접사 영문명 -> 약어/사용 위치 매핑. 신규 접사는 addAffix()로 등록한다. */
	private final HashMap<String, AffixRule> affixRules = new HashMap<>();
	/** 시스템 속성으로 지정된 최대 약어 길이. 조회 실패 시 4. */
	public int MAX_ABBRIVATION_LENGTH;
	/** true이면 숫자를 뒤에 오는 단어 약어에 붙이고, false이면 전체 약어 마지막에 붙인다. */
	public boolean NUMBER_ATTACH_TO_NEXT_WORD = false;

	public AbbrAutoGeneratorAction() {
		// 기본 접사 규칙. 신규 접사는 아래 형식으로 추가할 수 있다.
		addAffix("BY", "BY", false);
		addAffix("PER", "PR", false);
		addAffix("BETWEEN", "BT", false);
		addAffix("TEMPORARY", "TP", false);
		addAffix("T", "TT", false);
		addAffix("TOTAL", "TT", false);
		addAffix("PERSON", "PE", true);
		addAffix("RE", "RE", false);
	}

	/**
	 * 접사 규칙을 수동으로 추가한다.
	 *
	 * @param englishName 입력 영문 접사명(예: Between)
	 * @param abbreviation 생성할 접사 약어(예: BT)
	 * @param suffix true이면 본문 단어 뒤, false이면 본문 단어 앞에 붙인다.
	 */
	public void addAffix(String englishName, String abbreviation, boolean suffix) {
		if (englishName == null || abbreviation == null || englishName.trim().length() == 0
				|| abbreviation.trim().length() == 0) {
			return;
		}
		affixRules.put(englishName.trim().toUpperCase(),
				new AffixRule(abbreviation.trim().toUpperCase(), suffix));
	}

	/** 숫자 부착 위치 정책을 설정한다. */
	public void setNumberAttachToNextWord(boolean enabled) {
		this.NUMBER_ATTACH_TO_NEXT_WORD = enabled;
	}
	
	@Override
	/** 약어 생성 요청을 처리하고 EN_NM/ABBR 결과를 반환한다. */
	public HashMap[] utwAbbrGenerator(HashMap in) throws Exception {
		DefaultAbbrGenDao dao = null;
		int abbrlen = 5;
		// 사전의 대문자 옵션을 읽지만, 현재 구현에서는 실제 대소문자 변환에 사용하지 않는다.
		String abbrup = "Y";
		
		// EnvironmentUtil을 통해 시스템 환경값을 조회한다. 조회 실패 시 기본값 4를 사용한다.
		MAX_ABBRIVATION_LENGTH = getMaxAbbreviationLength();
		
		HashMap[] result = null;
		
		try {
			dao = (DefaultAbbrGenDao) ApplicationContextProvider.getBean(DefaultAbbrGenDao.class);
			HashMap<String, String> dicoption = dao.getDicOption(in);
			
			if(DataUtil.isEmpty(dicoption)) {
				return null;
			}
			
			abbrlen = MAX_ABBRIVATION_LENGTH;
			abbrup = dicoption.get("UTW_ABBR_UPPERCASE_YN");
			this.registeredWords = dao.getAbbrAll(in);
			// 의미가 약한 관사·전치사 등은 약어 구성에서 제외한다.
			this.invalidWords.add("A");
			this.invalidWords.add("AN");
			this.invalidWords.add("THE");
			this.invalidWords.add("OF");
			this.invalidWords.add("FOR");
			this.invalidWords.add("TO");
			this.invalidWords.add("BY");
			this.invalidWords.add("OR");
			this.invalidWords.add("AND");
			this.invalidWords.add("AS");
			
			result = new HashMap[1];
			result[0] = new HashMap<>();
			// EN_NM은 [[asdasd]]처럼 중첩 배열/컬렉션으로 전달될 수 있으므로 실제 문자열을 추출한다.
			String enNm = extractFirstString(in.get("EN_NM"));
			List<String> words = getSplitWord(enNm);
			List<String> makedList = cleanInvalidWord(words, new HashMap<>());
			// UTW_DIC_ID가 a인 사전만 별도 약어 규칙을 적용한다.
			String dicId = String.valueOf(in.get("UTW_DIC_ID"));
			String addAbbr;
			if ("a".equalsIgnoreCase(dicId)) {
				addAbbr = genAbbrForDictionaryA(makedList, abbrlen);
			} else if ("b".equalsIgnoreCase(dicId)) {
				addAbbr = genAbbrForDictionaryB(makedList, abbrlen);
			} else {
				addAbbr = genAbbr(makedList, abbrlen);
			}
			result[0].put("EN_NM", enNm);
			result[0].put("ABBR", addAbbr);
			
		} finally {
			DBUtil.release((JFDao) dao);
		}
		
		return result;
	}
	
	/**
	 * 정제된 단어 목록으로 약어를 만든다.
	 * 복합어는 각 단어의 자음에 길이를 균등 배분하고 숫자는 해당 단어 뒤에 붙인다.
	 * 결과가 기존 약어와 충돌하면 자음 선택 상태를 바꿔 대체 조합을 탐색한다.
	 */
	public String genAbbr(List<String> wordList, int abbrlen) {
		String retStr = "";
		List<String> consonantList = new ArrayList<>();
		List<String> orgWordList = new ArrayList<>();
		List<String> numberList = new ArrayList<>();
		List<String> subStrList = new ArrayList<>();
		// orgWordList와 subStrList는 확장/충돌 회피용으로 수집되지만 현재 결과 조합에는 사용되지 않는다.
		
		// 단어가 없으면 생성할 약어도 없다.
		if(wordList.size()<=0) {
			return retStr;
			// 한 단어가 이미 제한 길이 이내이면 원문을 그대로 사용한다.
		}else if(wordList.size()<=1 && ((String) wordList.get(0)).length()<= abbrlen) {
			return retStr = wordList.get(0);
		}
		
		String cosonantWord = "";
		String orgWord = "";
		String numberWord = "";
		String subStr = "";
		int wordCnt = 0;
		// 복합어의 약어 후보를 자음, 숫자, 알파벳으로 나누어 수집한다.
		wordCnt = wordList.size();
		
		for(int i=0; i <wordList.size(); i++) {
			cosonantWord = getConsonantWord(wordList.get(i),wordCnt);
			orgWord = getConsonantWord2(wordList.get(i),wordCnt);
			numberWord = getNumberWord(wordList.get(i));
			// 약어의 기본 골격이 되는 자음
			if(cosonantWord != null && cosonantWord.length()>0) {
				consonantList.add(cosonantWord);
			}
			// 원래 단어에 포함된 숫자
			if(numberWord != null && numberWord.length()>0) {
				numberList.add(numberWord);
			}
			// 숫자를 제외한 알파벳(현재 구현에서는 수집만 한다)
			if(orgWord != null && orgWord.length()>0) {
				orgWordList.add(orgWord);
			}
			
		}
		// 전체 길이를 단어 수로 나눠 각 단어에 배정할 자릿수를 계산한다.
		int avg = abbrlen / consonantList.size();
		// 나머지 자리는 앞쪽 단어부터 하나씩 추가한다.
		int mod = abbrlen % consonantList.size();
		
		int assignLength =0;
		int consonantListSize =0;
		int[] assignLengthArr = new int[consonantList.size()];
		
		for(int j=0;j<consonantList.size();j++) {
			assignLength = avg + ((j<mod)? 1:0);
			assignLengthArr[j] = assignLength;
			
			consonantListSize += ((String) consonantList.get(j)).length();
			
			if(assignLength > ((String) consonantList.get(j)).length()) {
				retStr = retStr + (String) consonantList.get(j);
			}else {
				// 배정 길이보다 자음이 길면 앞부분만 사용하고 나머지는 후보로 보관한다.
				retStr = retStr + ((String) consonantList.get(j)).substring(0,assignLength);
				subStr = ((String) consonantList.get(j).substring(assignLength));
				
				// 잘린 뒷부분은 충돌 회피 시 재조합할 수 있도록 저장한다.
				if(subStr != null && subStr.length()>0) {
					subStrList.add(subStr);
				}
			}
			
			for(int k=0; k<numberList.size();k++) {
				//알파벳 뒤에 숫자 붙이기
				if(k==j) {
					retStr += numberList.get(k) != null ? numberList.get(k).toString() : "";
				}
			}
		}
		// 생성 결과가 이미 등록된 약어이면 자음 선택 상태를 바꿔 재시도한다.
		if(isExistAbbreviation(retStr)) {
			// 각 자음의 원문과 현재 결과에 포함되는지 여부(Y/N)를 관리한다.
			String[][] bitConsonantWordArr = new String[2][consonantListSize];
			int index =0;
			
			for(int i=0; i< consonantList.size(); i ++) {
				String cStr = consonantList.get(i).toString();
				char cChar;
				int aLength = assignLengthArr[i];
				
				for(int j =0; j<cStr.length();j++) {
					cChar = cStr.charAt(j);
					if(j < aLength) {
						bitConsonantWordArr[0][index] = cChar+"";
						bitConsonantWordArr[1][index] = "Y";
					}else {
						bitConsonantWordArr[0][index] = cChar+"";
						bitConsonantWordArr[1][index] = "N";	
					}
					
					index++;
				}
			}
			
			if(!isExistAbbreviation(retStr)) {
				return retStr;
			}else {
				String bitYn ="";
				int breakIndex =0;
				String[][] tempBitConsonantWordArr = new String[2][consonantListSize];
				
				copyCallByValue(bitConsonantWordArr, tempBitConsonantWordArr);
				
				for(int k=abbrlen;k>0;k--) {
					breakIndex =0;
					//복사
					copyCallByValue(tempBitConsonantWordArr, bitConsonantWordArr);
						
					for(int i =0; i<(bitConsonantWordArr[0]).length; i++) {
						
						bitYn = bitConsonantWordArr[1][i];
						
						if("Y".equals(bitYn)) {
							//0부터 증가
							breakIndex++;
							//최대지정자리수의 -1과 증가 값이 해당 배열 N으로 변경
							if(breakIndex == k-1) {
								bitConsonantWordArr[1][i] = "N";
							}
						}
					}
					
					retStr = resultStr(bitConsonantWordArr);
					
					if((bitConsonantWordArr[0]).length>abbrlen) {
						for(int n = (bitConsonantWordArr[0]).length -1; n>=0;n--) {
							bitYn = bitConsonantWordArr[1][n];
							if("N".equals(bitYn)) {
								bitConsonantWordArr[1][n] = "Y";
								retStr = resultStr(bitConsonantWordArr);
								if(!isExistAbbreviation(retStr)) {
									return retStr;
								}
								
								bitConsonantWordArr[1][n] = "N";
							}
						}
					}
					
					//변경된 약어 중복 체크
					if(!isExistAbbreviation(retStr)) {
						return retStr;
					}
				}
			}
		}
		//기존 약어가 존재할경우 처리 끝
		return retStr;
	}

	/**
	 * UTW_DIC_ID가 b인 사전에 적용하는 약어 생성 규칙이다.
	 * 모음을 제외한 자음을 사용하고, '_'는 복합어 구분자로 보존한다.
	 */
	private String genAbbrForDictionaryB(List<String> wordList, int abbrlen) {
		// '_'는 단어 사이의 구분자이므로 각 부분을 따로 축약한 뒤 다시 보존한다.
		if (wordList.size() == 1 && wordList.get(0).indexOf('_') >= 0) {
			StringBuilder composite = new StringBuilder();
			String[] parts = wordList.get(0).toUpperCase().split("_", -1);
			for (int i = 0; i < parts.length; i++) {
				if (i > 0) {
					composite.append('_');
				}
				composite.append(getBConsonants(parts[i]));
			}
			String availableComposite = findAvailableAbbreviation(composite.toString());
			return availableComposite == null ? composite.toString() : availableComposite;
		}

		List<String> words = new ArrayList<>();
		for (String source : wordList) {
			String[] parts = source.toUpperCase().split("_", -1);
			for (String part : parts) {
				if (part.length() > 0) {
					words.add(getBConsonants(part));
				}
			}
		}

		String base = buildBAbbreviation(words, 4);
		String available = findAvailableAbbreviation(base);
		if (available != null) {
			return available;
		}

		// 중복 시 뒤 단어부터 차순위 자음 조합을 적용한다.
		for (int wordIndex = words.size() - 1; wordIndex >= 0; wordIndex--) {
			List<String> variants = getLetterVariants(words.get(wordIndex), 6);
			for (String variant : variants) {
				List<String> changed = new ArrayList<>(words);
				changed.set(wordIndex, getBConsonants(variant));
				for (int length = 4; length <= 6; length++) {
					available = findAvailableAbbreviation(buildBAbbreviation(changed, length));
					if (available != null) {
						return available;
					}
				}
			}
		}

		return buildBAbbreviation(words, Math.min(6, Math.max(4, abbrlen)));
	}

	/** 단어의 첫 영문자와 이후 자음만 남긴다. 연속된 동일 자음은 하나로 줄인다. */
	private String getBConsonants(String word) {
		StringBuilder result = new StringBuilder();
		char previous = 0;
		for (int i = 0; i < word.length(); i++) {
			char ch = Character.toUpperCase(word.charAt(i));
			if (!isEnglishLetter(ch)) {
				continue;
			}
			if (result.length() == 0) {
				result.append(ch);
				previous = ch;
			} else if (isConsonant(ch) && ch != previous) {
				result.append(ch);
				previous = ch;
			}
		}
		return result.toString();
	}

	/** 모든 단어에서 최소 한 글자씩 선별하고, 부족한 자리는 뒤 단어부터 보충한다. */
	private String buildBAbbreviation(List<String> words, int length) {
		StringBuilder result = new StringBuilder();
		if (words.isEmpty()) {
			return "";
		}

		// 먼저 모든 단어를 결과에 포함한다.
		for (String word : words) {
			if (word.length() > 0) {
				result.append(word.charAt(0));
			}
		}

		// 남은 자리는 뒤 단어의 뒤쪽 자음부터 추가한다. NPBD 예시를 만족한다.
		for (int wordIndex = words.size() - 1; wordIndex >= 0 && result.length() < length; wordIndex--) {
			String word = words.get(wordIndex);
			for (int charIndex = word.length() - 1; charIndex >= 1 && result.length() < length; charIndex--) {
				result.append(word.charAt(charIndex));
			}
		}
		return result.substring(0, Math.min(length, result.length()));
	}

	/**
	 * UTW_DIC_ID가 a인 사전에 적용하는 약어 생성 규칙이다.
	 * 기본 후보는 자음으로 만들고, 중복이면 뒤 단어부터 원문 문자 조합을 넓혀 재시도한다.
	 * 모든 중복 검사는 반드시 isExistAbbreviation()을 통해 수행한다.
	 */
	private String genAbbrForDictionaryA(List<String> wordList, int abbrlen) {
		List<String> letterWords = new ArrayList<>();
		List<String> wordNumbers = new ArrayList<>();
		String number = "";
		String pendingNumber = "";
		AffixInfo affix = extractAffix(wordList);

		for (String source : affix.bodyWords) {
			String word = source == null ? "" : source.toUpperCase();
			StringBuilder letters = new StringBuilder();
			StringBuilder localNumber = new StringBuilder();
			for (int i = 0; i < word.length(); i++) {
				char ch = word.charAt(i);
				if (ch >= '0' && ch <= '9') {
					localNumber.append(ch);
				} else if (isEnglishLetter(ch)) {
					letters.append(ch);
				}
			}
			if (letters.length() > 0) {
				letterWords.add(letters.toString());
				if (NUMBER_ATTACH_TO_NEXT_WORD) {
					wordNumbers.add(pendingNumber + localNumber.toString());
					pendingNumber = "";
				} else {
					number += localNumber.toString();
				}
			} else if (NUMBER_ATTACH_TO_NEXT_WORD) {
				// 숫자 단독 토큰은 다음 영문 단어에 연결한다.
				pendingNumber += localNumber.toString();
			} else {
				number += localNumber.toString();
			}
		}
		if (NUMBER_ATTACH_TO_NEXT_WORD) {
			// 뒤에 영문 단어가 없는 숫자는 고립 숫자이므로 최종 위치에 둔다.
			number = pendingNumber;
		}

		// 숫자만으로는 약어를 만들 수 없다. 숫자는 항상 최종 약어의 끝에 둔다.
		if (letterWords.isEmpty()) {
			return appendAffix("", affix, number);
		}

		String original = joinWords(letterWords);
		if (number.length() == 0 && affix.isEmpty() && letterWords.size() == 1 && original.length() <= 4) {
			return original.toUpperCase();
		}

		List<String> consonants = new ArrayList<>();
		for (String word : letterWords) {
			consonants.add(getAConsonants(word));
		}

		String base = appendAffix(buildUniformForA(consonants, wordNumbers, 4), affix, number);
		String available = findAvailableAbbreviation(base);
		if (available != null) {
			return available;
		}

		// 중복 시 뒤 단어부터 원문 문자의 차순위 조합을 적용한다.
		for (int wordIndex = letterWords.size() - 1; wordIndex >= 0; wordIndex--) {
			List<String> variants = getLetterVariants(letterWords.get(wordIndex), 6);
			for (String variant : variants) {
				List<String> changed = new ArrayList<>(consonants);
				changed.set(wordIndex, variant);
				for (int length = 4; length <= 6; length++) {
					String candidate = appendAffix(buildUniformForA(changed, wordNumbers, length), affix, number);
					available = findAvailableAbbreviation(candidate);
					if (available != null) {
						return available;
					}
				}
			}
		}

		// 모든 후보가 사용 중이면 최대 6자리 범위에서 길이를 늘려 마지막 후보를 반환한다.
		return appendAffix(buildUniformForA(consonants, wordNumbers,
				Math.min(6, Math.max(4, abbrlen))), affix, number);
	}

	/** 문자 길이를 단어별로 배분한 뒤, 해당 단어의 숫자를 바로 뒤에 붙인다. */
	private String buildUniformForA(List<String> words, List<String> numbers, int length) {
		if (!NUMBER_ATTACH_TO_NEXT_WORD) {
			return buildUniform(words, length);
		}
		StringBuilder result = new StringBuilder();
		int average = length / words.size();
		int remainder = length % words.size();
		for (int i = 0; i < words.size(); i++) {
			int count = average + (i < remainder ? 1 : 0);
			result.append(words.get(i).substring(0, Math.min(count, words.get(i).length())));
			if (i < numbers.size()) {
				result.append(numbers.get(i));
			}
		}
		return result.toString();
	}

	/** 접사 약어를 본문 앞/뒤에 붙인다. 숫자 위치는 NUMBER_ATTACH_TO_NEXT_WORD 설정을 따른다. */
	private String appendAffix(String abbreviation, AffixInfo affix, String number) {
		return affix.prefix + abbreviation.toUpperCase() + affix.suffix + number;
	}

	/**
	 * 영문 접사명을 약어로 변환한다.
	 * BY/Per/Between/Temporary/T(Total)/Person/Re를 인식하며, Person은 단어 뒤에 붙인다.
	 */
	private AffixInfo extractAffix(List<String> words) {
		AffixInfo result = new AffixInfo();
		for (String source : words) {
			String word = source == null ? "" : source.toUpperCase();
			AffixRule rule = affixRules.get(word);
			if (rule == null) {
				result.bodyWords.add(source);
			} else if (rule.suffix) {
				result.suffix += rule.abbreviation;
			} else {
				result.prefix += rule.abbreviation;
			}
		}
		return result;
	}

	/** 최대 약어 길이를 읽는다. 값이 없거나 잘못된 값이면 기본값 4를 사용한다. */
	private int getMaxAbbreviationLength() {
		String property = EnvironmentUtil.getSysStr("MAX_ABBRIVATION_LENGTH");
		if (property == null || property.trim().length() == 0) {
			return 4;
		}
		try {
			int length = Integer.parseInt(property.trim());
			return length > 0 ? length : 4;
		} catch (NumberFormatException ignore) {
			return 4;
		}
	}

	private static class AffixRule {
		private final String abbreviation;
		private final boolean suffix;

		private AffixRule(String abbreviation, boolean suffix) {
			this.abbreviation = abbreviation;
			this.suffix = suffix;
		}
	}

	private static class AffixInfo {
		private final List<String> bodyWords = new ArrayList<>();
		private String prefix = "";
		private String suffix = "";

		private boolean isEmpty() {
			return prefix.length() == 0 && suffix.length() == 0;
		}
	}

	/** 첫 글자가 모음이면 첫 모음을 허용하고, 그 외에는 자음과 중복되지 않는 문자를 선별한다. */
	private String getAConsonants(String word) {
		StringBuilder result = new StringBuilder();
		char previous = 0;
		for (int i = 0; i < word.length(); i++) {
			char ch = word.charAt(i);
			if (i == 0 && isVowel(ch)) {
				result.append(ch);
				previous = ch;
			} else if (isConsonant(ch)) {
				// 같은 자음이 반복되더라도 다음 문자도 자음이면 대표 형태(ADDR)를 유지한다.
				// 반복 뒤에 모음이 오면 한 글자로 줄인다(COMMENT -> CMNT).
				boolean keepRepeatedConsonant = ch == previous
						&& i + 1 < word.length() && isConsonant(word.charAt(i + 1));
				if (ch != previous || keepRepeatedConsonant) {
					result.append(ch);
				}
				previous = ch;
			}
		}
		return result.toString();
	}

	/** 여러 단어에서 약어 길이를 균등하게 배분한다. */
	private String buildUniform(List<String> words, int length) {
		if (words.isEmpty() || length <= 0) {
			return "";
		}
		StringBuilder result = new StringBuilder();
		int average = length / words.size();
		int remainder = length % words.size();
		for (int i = 0; i < words.size(); i++) {
			int count = average + (i < remainder ? 1 : 0);
			String word = words.get(i);
			result.append(word.substring(0, Math.min(count, word.length())));
		}
		return result.toString();
	}

	/** 중복 여부는 이 메서드 하나로만 확인한다. */
	private String findAvailableAbbreviation(String candidate) {
		if (candidate == null || candidate.length() == 0 || isExistAbbreviation(candidate)) {
			return null;
		}
		return candidate;
	}

	private String appendNumber(String abbreviation, String number) {
		return abbreviation.toUpperCase() + number;
	}

	private String joinWords(List<String> words) {
		StringBuilder result = new StringBuilder();
		for (String word : words) {
			result.append(word);
		}
		return result.toString();
	}

	private boolean isEnglishLetter(char ch) {
		return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
	}

	private boolean isVowel(char ch) {
		return ch == 'A' || ch == 'E' || ch == 'I' || ch == 'O' || ch == 'U';
	}

	private boolean isConsonant(char ch) {
		return isEnglishLetter(ch) && !isVowel(Character.toUpperCase(ch));
	}

	/** 한 단어의 원문 문자로 만들 수 있는 차순위 후보를 순서대로 반환한다. */
	private List<String> getLetterVariants(String word, int maxLength) {
		List<String> result = new ArrayList<>();
		makeLetterVariants(word, 0, "", maxLength, result);
		return result;
	}

	private void makeLetterVariants(String word, int index, String value, int maxLength, List<String> result) {
		if (value.length() > 0 && !result.contains(value)) {
			result.add(value);
		}
		if (value.length() == maxLength) {
			return;
		}
		for (int i = index; i < word.length(); i++) {
			makeLetterVariants(word, i + 1, value + word.charAt(i), maxLength, result);
		}
	}

	/** 중첩 배열/컬렉션 입력에서 첫 번째 실제 문자열을 추출한다. */
	private String extractFirstString(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof CharSequence) {
			return value.toString();
		}
		if (value.getClass().isArray()) {
			return Array.getLength(value) == 0 ? "" : extractFirstString(Array.get(value, 0));
		}
		if (value instanceof Iterable<?>) {
			java.util.Iterator<?> iterator = ((Iterable<?>) value).iterator();
			return iterator.hasNext() ? extractFirstString(iterator.next()) : "";
		}
		return String.valueOf(value);
	}
	/** 금칙어를 제거하고 유효한 단어가 하나도 없으면 예외를 발생시킨다. */
	public List<String> cleanInvalidWord(List<String> wordList, HashMap in) throws Exception{
		List<String> resultList = new ArrayList<>();
		String word = null;
		for(int i=0;i<wordList.size();i++) {
			word = wordList.get(i);
			
			if(this.invalidWords.indexOf(word.toUpperCase()) == -1) {
				resultList.add(word);
			}
		}
		
		if(resultList.size()==0) {
			throw new MMException("유효한 단어가 없습니다.");
		}
		
		return resultList;
	}
	/** 영문명을 공백 기준으로 분리한다. 연속 공백은 하나의 구분자로 취급한다. */
	private List<String> getSplitWord(String words){
		List<String> result = new ArrayList<>();
		String word = new String();
		for(int i=0;i<words.length();i++) {
			char chr = words.charAt(i);
			switch(chr) {
			case ' ':
				if(word.length() == 0) {
					break;
				}
				// 공백을 만나면 지금까지 모은 단어를 저장한다.
				result.add(word);
				
				word = new String();
				break;
			default:
				// 공백이 아니면 현재 단어에 문자를 이어 붙인다.
				word = word+ chr;
				break;
			}
		}
		
		result.add(word);
		return result;
	}
	
	/** 단일 단어에서 첫 글자와 중복되지 않는 영문 자음을 추출한다. */
	public String getConsonantWord(String words) {
		String retStr = "";
		if(words.length()>0) {
			char chr = words.charAt(0);
			if((chr >= 'A' && chr<='Z')||(chr >='a' && chr<='z')) {
				retStr = "" + words.charAt(0);
			}
		}
		
		for(int i=1;i<words.length();i++) {
			char chr = words.charAt(i);
			if(DataUtil.isConsonant(chr)&& chr != words.charAt(i-1) 
					&& ((chr >= 'A' && chr<='Z')||(chr >='a' && chr<='z'))) {
				retStr = retStr + chr;
			}
		}
		
		return retStr;
	}
	
	/** 기존 약어 목록에 생성 결과가 있는지 확인한다. */
	public boolean isExistAbbreviation(String abbreviation) {
		if (abbreviation == null || this.registeredWords == null) {
			return false;
		}
		String target = abbreviation.toUpperCase(Locale.ENGLISH);
		for (String registered : this.registeredWords) {
			if (registered != null && target.equals(registered.toUpperCase(Locale.ENGLISH))) {
				return true;
			}
		}
		return false;
	}
	
	/** 비트 배열에서 Y 문자를 먼저, N 문자를 뒤에 배치해 약어를 재구성한다. */
	public String resultStr(String[][] bitConsonantWordArr) {
		String bitStr = "";
		String bitYn = "";
		String retStr = "";
		String tmpStr = "";
		
		for(int j=0;j<(bitConsonantWordArr[0]).length;j++) {
			bitStr = bitConsonantWordArr[0][j];
			bitYn = bitConsonantWordArr[1][j];
			
			if("Y".equals(bitYn)) {
				retStr = retStr + bitStr;
			}
			
			if("N".equals(bitYn)) {
				tmpStr = tmpStr + bitStr;
			}
		}
		
		retStr = retStr + tmpStr;
		return retStr;
	}
	
	/** 충돌 회피 탐색 중 원본 상태가 변하지 않도록 2차원 배열 값을 복사한다. */
	public void copyCallByValue(String[][] srcArr, String[][]trgArr) {
		for(int i=0;i<srcArr.length;i++) {
			for(int j=0;j < (srcArr[i]).length;j++) {
				trgArr[i][j] = srcArr[i][j];
			}
		}
	}
	
	/** 단어 수에 따라 약어용 자음을 추출한다. 복합어는 모든 단어를 자음 위주로 만든다. */
	public String getConsonantWord(String words, int wordsCnt) {
		String retStr = "";
		
		if(wordsCnt > 1) {
			for(int i =0; i <words.length(); i++) {
				char chr = words.charAt(i);
				
				if(i==0) {
					if(DataUtil.isConsonant(chr)&& (chr >= 'A' && chr<='Z')||(chr >='a' && chr<='z')) {
						retStr = retStr + chr;
					}
				}else {
					//자음이고 직전단어와 동일하지 않는 알파벳이면 적재
					if(DataUtil.isConsonant(chr)&& chr != words.charAt(i-1) 
							&& ((chr >= 'A' && chr<='Z')||(chr >='a' && chr<='z'))) {
						retStr = retStr + chr;
					}
				}
			}
		}else {
			//첫번째 단어는 무조건 약어 구성 단어로 사용
			if(words.length()>0) {
				char chr = words.charAt(0);
				if((chr >= 'A' && chr<='Z')||(chr >='a' && chr<='z')) {
					retStr = ""+ words.charAt(0);
				}
			}
			//두번째 이후 자음 가져오기
			for(int i=0;i<words.length();i++) {
				char chr = words.charAt(i);
				//자음이고 직전단어와 동일하지 않는 알파벳이면 적재
				if(DataUtil.isConsonant(chr)&& chr != words.charAt(i-1) 
						&& ((chr >= 'A' && chr<='Z')||(chr >='a' && chr<='z'))) {
					retStr = retStr + chr;
				}
			}
		}
		return retStr;
	}

	/** 자음이 아닌 알파벳도 포함한 보조 후보를 만든다. 현재 genAbbr에서는 수집만 한다. */
	public String getConsonantWord2(String words, int wordsCnt) {
		String retStr = "";
		if(wordsCnt>1) {
			//두번째 이후 약어를 구성할 자음 모음 가져오기
			for(int i=0;i<words.length();i++) {
				char chr = words.charAt(i);
				
				if(i==0) {
					//자음이고 알파벳이면 적재
					if((chr >= 'A' && chr<='Z')||(chr >='a' && chr<='z')) {
						retStr = retStr + chr;
					}
				}else {
					//직전단어와 동일하지 않는 알파벳이면 적재
					if(chr != words.charAt(i-1) && (chr >= 'A' && chr<='Z')||(chr >='a' && chr<='z')) {
						retStr = retStr + chr;
					}
				}
			}
		}else {
			//첫번째 단어는 무조건 약어 구성 단어로 사용
			if(words.length() > 0) {
				char chr = words.charAt(0);
				
				if((chr >= 'A' && chr<='Z')||(chr >='a' && chr<='z')) {
					retStr = retStr + words.charAt(0);
				}
			}
			
			//두번째 이후 약어를 구성할 자음 가져오기
			for(int i=1;i<words.length();i++) {
				char chr = words.charAt(i);
				//자음이고 앞단어와 중복이 아닌 알파벳이면
				if(chr != words.charAt(i-1) && (chr >= 'A' && chr<='Z')||(chr >='a' && chr<='z')) {
					retStr = retStr + chr;
				}
			}
		}
	return retStr;
	}
	
	/** 단어에서 숫자만 순서대로 추출한다. */
	public String getNumberWord(String words) {
		String retStr = "";
		for(int i=0;i<words.length();i++) {
			char chr = words.charAt(i);
			//숫자 추출
			if(chr >='0' && chr <='9') {
				retStr = retStr + chr;
			}
		}
		return retStr;
	}
}
