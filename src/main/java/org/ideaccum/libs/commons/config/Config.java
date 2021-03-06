package org.ideaccum.libs.commons.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.bind.JAXB;
import javax.xml.bind.annotation.XmlElement;

import org.ideaccum.libs.commons.config.exception.ConfigException;
import org.ideaccum.libs.commons.util.ClassUtil;
import org.ideaccum.libs.commons.util.PropertiesUtil;
import org.ideaccum.libs.commons.util.ResourceUtil;
import org.ideaccum.libs.commons.util.StringUtil;

/**
 * 外部定義されたプロパティリソースへのアクセスを行うためのインタフェースを提供します。<br>
 * <p>
 * このクラスではプロパティを参照する際に{@link org.ideaccum.libs.commons.config.ConfigName}を継承したキークラスを用いてアクセスします。<br>
 * {@link org.ideaccum.libs.commons.config.ConfigName}は、プロパティ名のリファクタリング効率を上げる目的と、プロパティ値取得時の型固定のために設置されています。<br>
 * また、このクラスではクラスローダー上で常に単一のインスタンスが提供されるシングルトンインスタンスを保持し、新規インスタンスを利用する場合、シングルトンで提供される値を継承させるかを選択できます。<br>
 * </p>
 * 
 *<!--
 * 更新日      更新者           更新内容
 * 2010/07/03  Kitagawa         新規作成
 * 2018/05/02  Kitagawa         再構築(SourceForge.jpからGitHubへの移行に併せて全面改訂)
 * 2019/05/04  Kitagawa         ConfigName継承サブクラスを設置した際にサブクラスを参照する前にgetMapを利用するとキーセットが取得できないため、reflection,jarライブラリを利用して強制的にサブクラスをクラスロードするように修正
 * 2019/10/29  Kitagawa         ConfigNameに対してプロパティ定義値型を限定する仕様とし、get*****系のメソッドをDeplicatedに変更({@link #get(ConfigName)}を追加)
 * 2019/11/07  Kitagawa         get*****系メソッドを削除し、{@link #get(ConfigName)}を利用させることを強制
 * 2019/11/07  Kitagawa         シングルトンインスタンス取得と個別インスタンス生成後に利用するAPI構成に変更
 * 2019/11/27  Kitagawa         XMLリソースからの読み込みはJAXBを用いた定義構成クラスをもとに解析する仕様に変更
 *-->
 */
public final class Config implements Serializable {

	/** シングルトンインスタンス */
	private static Config global = new Config(false);

	/** ロックオブジェクト */
	private Object lock = new Object();

	/** シングルトンインスタンス値継承フラグ */
	private boolean inheritGlobal;

	/** 環境設定プロパティオブジェクト */
	private Properties properties;

	/** プロパティ定義内容レンダラオブジェクト */
	private ConfigValueRenderer renderer;

	/** プロパティパースオブジェクト */
	private Map<Class<? extends ConfigValueParser<?>>, ConfigValueParser<?>> parsers;

	/**
	 * コンストラクタ<br>
	 * @param inheritGlobal シングルトンインスタンス値継承フラグ
	 */
	private Config(boolean inheritGlobal) {
		super();
		this.inheritGlobal = inheritGlobal;
		this.properties = new Properties();
		this.renderer = null;
		this.parsers = new HashMap<>();
	}

	/**
	 * クラスローダー上で単一インスタンスが保証されるグローバル環境設定情報を取得します。<br>
	 * @return グローバル環境設定情報
	 */
	public static Config global() {
		return global;
	}

	/**
	 * グローバル環境設定情報とは別のインスタンスとして環境設定情報を生成します。<br>
	 * @param inheritGlobal 個別環境設定情報に情報が存在しない場合はグローバル環境設定情報を継承して提供する場合にtrueを指定
	 * @return 環境設定情報
	 */
	public static Config create(boolean inheritGlobal) {
		return new Config(inheritGlobal);
	}

	/**
	 * グローバル環境設定情報とは別のインスタンスとして環境設定情報を生成します。<br>
	 * 個別環境設定情報に情報が存在しない場合はグローバル環境設定情報を継承して値が提供されます。<br>
	 * @return 環境設定情報
	 */
	public static Config create() {
		return create(true);
	}

	/**
	 * オブジェクト情報を文字列として提供します。<br>
	 * @return オブジェクト情報文字列
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return properties.toString();
	}

	/**
	 * オブジェクトハッシュコードを取得します。<br>
	 * @return オブジェクトハッシュコード
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((properties == null) ? 0 : properties.hashCode());
		return result;
	}

	/**
	 * オブジェクト等価比較を行います。<br>
	 * @return 等価の場合にtrueを返却
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (object == null) {
			return false;
		}
		if (getClass() != object.getClass()) {
			return false;
		}
		Config other = (Config) object;
		if (properties == null) {
			if (other.properties != null) {
				return false;
			}
		} else if (!properties.equals(other.properties)) {
			return false;
		}
		return true;
	}

	/**
	 * プロパティリソース内容を読み込みクラスインスタンスに展開します。<br>
	 * @param filePath プロパティリソースパス
	 * @param mode プロパティ読み込み時の挙動
	 * @return ロード後の自身のインスタンス
	 */
	public Config load(String filePath, ConfigLoadMode mode) {
		synchronized (lock) {
			try {
				/*
				 * 対象プロパティ読み込み
				 */
				Properties loaded = loadFromProperties(filePath);

				/*
				 * プロパティ情報反映
				 */
				storeProperties(loaded, mode);

				return this;
			} catch (Throwable e) {
				throw new ConfigException(e);
			}
		}
	}

	/**
	 * プロパティリソース内容を読み込みクラスインスタンスに展開します。<br>
	 * このメソッドによる読み込みは現在管理されているプロパティ情報を破棄して新たに読み込みます。<br>
	 * 読み込み方法を指定してプロパティを反映する場合は{@link #load(String, ConfigLoadMode)}又は、{@link #load(String, ConfigLoadMode, String)}を利用して下さい。<br>
	 * @param filePath プロパティリソースパス
	 * @return ロード後の自身のインスタンス
	 */
	public Config load(String filePath) {
		return load(filePath, ConfigLoadMode.REPLACE_ALL);
	}

	/**
	 * プロパティリソースの通常読込後、差分上書き読み込みします。<br>
	 * このメソッドは標準プロパティリソース及び、環境毎の差分プロパティが提供される場合に、標準内容に対して環境毎の差分を適用する場合に利用することを想定したメソッドです。<br>
	 * @param filePath 標準プロパティリソースパス
	 * @param mode プロパティ読み込み時の挙動(この挙動は標準プロパティに対する読み込み挙動となります)
	 * @param extendProps 差分読み込みプロパティリソースパス
	 * @return ロード後の自身のインスタンス
	 */
	public Config load(String filePath, ConfigLoadMode mode, String... extendProps) {
		if (extendProps == null || extendProps.length <= 0) {
			return load(filePath, mode);
		}
		synchronized (lock) {
			Config config = load(filePath, mode);
			for (String extendProp : extendProps) {
				config.load(extendProp, ConfigLoadMode.REPLACE_EXISTS);
			}
			return config;
		}
	}

	/**
	 * プロパティリソースの通常読込後、差分上書き読み込みします。<br>
	 * このメソッドは標準プロパティリソース及び、環境毎の差分プロパティが提供される場合に、標準内容に対して環境毎の差分を適用する場合に利用することを想定したメソッドです。<br>
	 * @param filePath 標準プロパティリソースパス
	 * @param extendProps 差分読み込みプロパティリソースパス
	 * @return ロード後の自身のインスタンス
	 */
	public Config load(String filePath, String... extendProps) {
		if (extendProps == null || extendProps.length <= 0) {
			return load(filePath);
		}
		synchronized (lock) {
			Config config = load(filePath);
			for (String extendProp : extendProps) {
				config.load(extendProp, ConfigLoadMode.REPLACE_EXISTS);
			}
			return config;
		}
	}

	/**
	 * XMLプロパティリソース内容を読み込みクラスインスタンスに展開します。<br>
	 * @param filePath XMLプロパティリソースパス
	 * @param mode プロパティ読み込み時の挙動
	 * @return ロード後の自身のインスタンス
	 */
	public Config load(Class<?> type, String filePath, ConfigLoadMode mode) {
		synchronized (lock) {
			try {
				/*
				 * 対象プロパティ読み込み
				 */
				Properties loaded = loadFromXml(type, filePath);

				/*
				 * プロパティ情報反映
				 */
				storeProperties(loaded, mode);

				return this;
			} catch (Throwable e) {
				throw new ConfigException(e);
			}
		}
	}

	/**
	 * XMLプロパティリソース内容を読み込みクラスインスタンスに展開します。<br>
	 * このメソッドによる読み込みは現在管理されているプロパティ情報を破棄して新たに読み込みます。<br>
	 * 読み込み方法を指定してプロパティを反映する場合は{@link #load(String, ConfigLoadMode)}又は、{@link #load(String, ConfigLoadMode, String)}を利用して下さい。<br>
	 * @param filePath XMLプロパティリソースパス
	 * @return ロード後の自身のインスタンス
	 */
	public Config load(Class<?> type, String filePath) {
		return load(type, filePath, ConfigLoadMode.REPLACE_ALL);
	}

	/**
	 * XMLプロパティリソースの通常読込後、差分上書き読み込みします。<br>
	 * このメソッドは標準プロパティリソース及び、環境毎の差分プロパティが提供される場合に、標準内容に対して環境毎の差分を適用する場合に利用することを想定したメソッドです。<br>
	 * @param filePath 標準XMLプロパティリソースパス
	 * @param mode プロパティ読み込み時の挙動(この挙動は標準プロパティに対する読み込み挙動となります)
	 * @param extendProps 差分読み込みXMLプロパティリソースパス
	 * @return ロード後の自身のインスタンス
	 */
	public Config load(Class<?> type, String filePath, ConfigLoadMode mode, String... extendProps) {
		if (extendProps == null || extendProps.length <= 0) {
			return load(type, filePath, mode);
		}
		synchronized (lock) {
			Config config = load(type, filePath, mode);
			for (String extendProp : extendProps) {
				config.load(type, extendProp, ConfigLoadMode.REPLACE_EXISTS);
			}
			return config;
		}
	}

	/**
	 * XMLプロパティリソースの通常読込後、差分上書き読み込みします。<br>
	 * このメソッドは標準プロパティリソース及び、環境毎の差分プロパティが提供される場合に、標準内容に対して環境毎の差分を適用する場合に利用することを想定したメソッドです。<br>
	 * @param filePath 標準XMLプロパティリソースパス
	 * @param extendProps 差分読み込みXMLプロパティリソースパス
	 * @return ロード後の自身のインスタンス
	 */
	public Config load(Class<?> type, String filePath, String... extendProps) {
		if (extendProps == null || extendProps.length <= 0) {
			return load(type, filePath);
		}
		synchronized (lock) {
			Config config = load(type, filePath);
			for (String extendProp : extendProps) {
				config.load(type, extendProp, ConfigLoadMode.REPLACE_EXISTS);
			}
			return config;
		}
	}

	/**
	 * プロパティリソースからプロパティを読み込みます。<br>
	 * @param filePath プロパティリソースパス
	 * @return 読み込まれたプロパティリソース
	 * @throws IOException 入出力例外が発生した場合にスローされます
	 */
	private Properties loadFromProperties(String filePath) throws IOException {
		Properties properties = new Properties();
		if (!StringUtil.isEmpty(filePath) && ResourceUtil.exists(filePath)) {
			properties = PropertiesUtil.load(filePath);
		}
		return properties;
	}

	/**
	 * XMLリソースからプロパティを読み込みます。<br>
	 * @param type XML構造クラス
	 * @param filePath プロパティリソースパス
	 * @return 読み込まれたプロパティリソース
	 * @throws IOException 入出力例外が発生した場合にスローされます
	 */
	private Properties loadFromXml(Class<?> type, String filePath) throws IOException {
		InputStream stream = null;
		try {
			stream = ResourceUtil.getInputStream(filePath);
			Object object = JAXB.unmarshal(stream, type);
			Properties properties = new Properties();
			analyzeXml(properties, null, object);
			return properties;
		} finally {
			if (stream != null) {
				stream.close();
			}
		}
	}

	/**
	 * XMLリソースから読み込まれたオブジェクトを元にプロパティリソースに展開します。<br>
	 * @param properties 展開先プロパティリソース
	 * @param activeName 解析中プロパティキー
	 * @param object 解析対象プロパティ要素
	 */
	private boolean analyzeXml(Properties properties, String activeName, Object object) {
		boolean reflected = false;
		for (Field field : ClassUtil.getFields(object.getClass())) {
			XmlElement element = ClassUtil.getAnnotation(field, XmlElement.class);
			if (element == null) {
				continue;
			}
			String elementName = !"##default".equals(element.name()) ? element.name() : field.getName();
			String actualName = StringUtil.isEmpty(activeName) ? elementName : activeName + "." + elementName;
			Object value = ClassUtil.getFieldValue(object, field);
			properties.put(actualName, value == null ? "" : value);
			if (value == null) {
				reflected = true;
			} else if (analyzeXml(properties, actualName, value)) {
				// 下位階層で反映された場合は自プロパティは除去
				properties.remove(actualName);
			} else {
				reflected = true;
			}
		}
		return reflected;
	}

	/**
	 * 読み込みモードごとに読み込まれたプロパティ情報をインスタンスに反映します。<br>
	 * @param loaded 読み込まれたプロパティ情報
	 * @param mode 読み込みモード
	 */
	private void storeProperties(Properties loaded, ConfigLoadMode mode) {
		if (mode == ConfigLoadMode.REPLACE_ALL || mode == null) {
			// すべてのプロパティを置き換える場合は現状の保持情報をクリア
			properties.clear();
			properties.putAll(loaded);
		} else if (mode == ConfigLoadMode.REPLACE_EXISTS) {
			// 既存プロパティに対しては上書きする場合は読み込んだプロパティをプット
			properties.putAll(loaded);
		} else if (mode == ConfigLoadMode.SKIP_EXISTS) {
			// 既存プロパティに対しては現状維持とする場合はプロパティごとに判定しながらプット
			for (Object key : loaded.keySet()) {
				if (properties.containsKey(key)) {
					continue;
				}
				Object value = loaded.get(key);
				properties.put(key, value);
			}
		}
	}

	/**
	 * 管理されているプロパティ情報を全てクリアします。<br>
	 */
	public void destroy() {
		synchronized (lock) {
			properties.clear();
		}
	}

	/**
	 * 他のプロパティ情報内容を自身のインスタンスにマージします。<br>
	 * マージされる対象はプロパティ内容のみで、プロパティ値レンダラは自身のレンダラが維持されることに注意してください。<br>
	 * @param other マージ元インスタンス
	 */
	@SuppressWarnings("static-access")
	public void merge(Config other) {
		if (other == null || other.equals(this)) {
			return;
		}
		this.global.properties.putAll(other.global.properties); // For other classloader
		this.properties.putAll(other.properties);
	}

	/**
	 * プロパティ定義内容レンダラオブジェクトを設定します。<br>
	 * レンダラオブジェクトを設定した場合、各種プロパティ値取得時にレンダラ処理で値補正が行われたうえで値が提供されます。<br>
	 * @param renderer プロパティ定義内容レンダラオブジェクト
	 */
	public void setRenderer(ConfigValueRenderer renderer) {
		synchronized (lock) {
			this.renderer = renderer;
		}
	}

	/**
	 * プロパティ上に管理されている値を必要に応じて補正した文字列で提供します。<br>
	 * @param name プロパティアクセスキー
	 * @param object プロパティ定義情報
	 * @return 必要に応じて補正した文字列
	 */
	private String bind(ConfigName<?> name, Object object) {
		String value = object == null ? "" : object.toString();
		if (inheritGlobal && global.renderer != null) {
			value = global.renderer.render(name, value);
		}
		if (renderer != null) {
			return renderer.render(name, value);
		} else {
			return value;
		}
	}

	/**
	 * プロパティ情報を文字列値として取得します。<br>
	 * @param name プロパティアクセスキー
	 * @return プロパティ情報
	 */
	public boolean isEmpty(ConfigName<?> name) {
		if (name == null) {
			return true;
		}
		boolean result = !properties.containsKey(name.getKey()) || StringUtil.isEmpty(properties.getProperty(name.getKey()));
		if (inheritGlobal && !result) {
			result = !global.properties.containsKey(name.getKey()) || StringUtil.isEmpty(global.properties.getProperty(name.getKey()));
		}
		return result;
	}

	/**
	 * 管理されているプロパティキーを{@link org.ideaccum.libs.commons.config.ConfigName}形式で取得します。<br>
	 * 但し、{@link org.ideaccum.libs.commons.config.ConfigName}として提供されないキーは除外されて提供されます。<br>
	 * @return 管理されているプロパティキー
	 */
	public Set<ConfigName<?>> keySet() {
		Set<ConfigName<?>> set = new HashSet<>();

		/*
		 * グローバル環境設定キー追加
		 */
		if (inheritGlobal) {
			for (Object key : global.properties.keySet()) {
				ConfigName<?> name = ConfigName.valueOf(key.toString());
				if (name != null && !set.contains(name)) {
					set.add(name);
				}
			}
		}

		/*
		 * 個別インスタンス環境設定キー追加
		 */
		for (Object key : properties.keySet()) {
			ConfigName<?> name = ConfigName.valueOf(key.toString());
			if (name != null && !set.contains(name)) {
				set.add(name);
			}
		}

		return set;
	}

	/**
	 * 管理されているプロパティ情報をマップ形式で取得します。<br>
	 * @return プロパティ情報マップオブジェクト
	 */
	public Map<String, Object> map() {
		Map<String, Object> map = new HashMap<>();
		//for (ConfigName<?> name : keySet()) {
		//	Object value = get(name);
		//	map.put(name.getKey(), bind(name, value));
		//}
		for (Object key : properties.keySet()) {
			String name = key.toString();
			Object value = properties.get(key);
			map.put(name, value);
		}
		return map;
	}

	/**
	 * 管理されているプロパティ情報をピリオド(".")をデリミタとして見なした階層形式のマップ形式として取得します。<br>
	 * foo.barというキーで定義されている場合、fooというキーで保持されたMap内にbarというキーで定義値が保持されます。<br>
	 * 当メソッドはVelocity等でEL表記アクセスを行うために設けられました。<br>
	 * @return 階層化されたプロパティ定義マップオブジェクト
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> tree() {
		Map<String, Object> map = new HashMap<>();
		Map<String, Object> rendered = map();
		for (String key : rendered.keySet()) {
			String[] tokens = key.split("\\.");
			Map<String, Object> active = map;
			for (int i = 0; i <= tokens.length - 2; i++) {
				String token = tokens[i];
				if (!active.containsKey(token)) {
					active.put(token, new HashMap<>());
				}
				Object entry = active.get(token);
				if (entry instanceof Map) {
					active = (Map<String, Object>) active.get(token);
				} else {
					// 階層途中のトークンで定義されているものはマップで上書き(外部からEL参照は不可となる)
					Map<String, Object> newmap = new HashMap<>();
					newmap.put("", entry);
					active.put(token, newmap);
					active = (Map<String, Object>) active.get(token);
				}
			}
			active.put(tokens[tokens.length - 1], map.get(key));
		}
		return map;
	}

	/**
	 * プロパティ情報を取得します。<br>
	 * プロパティ値を取得する際のパーサーを強制的に指定して値を取得します。<br>
	 * 通常は{@link org.ideaccum.libs.commons.config.ConfigName}を継承したプロパティーキークラスが提供するパーサーを利用する{@link #get(ConfigName)}を利用します。<br>
	 * @param name プロパティアクセスキー
	 * @param parser プロパティ値パーサー
	 * @return プロパティ情報
	 */
	public <T> T get(ConfigName<?> name, ConfigValueParser<T> parser) {
		if (name == null) {
			return null;
		}
		String value = properties.contains(name.getKey()) ? properties.getProperty(name.getKey()) : !inheritGlobal ? null : global.properties.getProperty(name.getKey());
		String render = bind(name, value);
		return parser.parse(render);
	}

	/**
	 * プロパティ情報を取得します。<br>
	 * @param name プロパティアクセスキー
	 * @return プロパティ情報
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(ConfigName<T> name) {
		if (name == null) {
			return null;
		}
		String value = properties.containsKey(name.getKey()) ? properties.getProperty(name.getKey()) : !inheritGlobal ? null : global.properties.getProperty(name.getKey());
		String render = bind(name, value);
		if (!parsers.containsKey(name.getParserClass())) {
			parsers.put(name.getParserClass(), ClassUtil.createInstance(name.getParserClass()));
		}
		return (T) parsers.get(name.getParserClass()).parse(render);
	}
}
