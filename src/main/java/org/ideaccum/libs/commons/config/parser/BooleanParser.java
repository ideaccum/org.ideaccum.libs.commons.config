package org.ideaccum.libs.commons.config.parser;

import org.ideaccum.libs.commons.config.ConfigValueParser;
import org.ideaccum.libs.commons.util.StringUtil;

/**
 * 定義値を真偽値としてパースする処理を提供します。<br>
 * <p>
 * プロパティ定義値を真偽値としてパースして提供します。<br>
 * </p>
 * 
 *<!--
 * 更新日      更新者           更新内容
 * 2019/10/29  Kitagawa         新規作成
 *-->
 */
public class BooleanParser implements ConfigValueParser<Boolean> {

	/**
	 * プロパティリソース定義文字列内容を実際に利用する際の型にパースして提供します。<br>
	 * @param value プロパティ定義値
	 * @return パース語定義値
	 * @see org.ideaccum.libs.commons.config.ConfigValueParser#parse(java.lang.String)
	 */
	@Override
	public Boolean parse(String value) {
		if (value == null) {
			return false;
		}
		return StringUtil.toPBoolean(value);
	}
}
