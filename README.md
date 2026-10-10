# GsonModelGenerator

编译期根据规格类生成 Gson `data class`。属性名保持可读，`@SerializedName` 的值在生成时就算好。规格类本身不会变成运行时模型。

运行时使用生成类，例如 `SparrowDemoLoginWire`，不要使用带 `@GenModel` 的规格类。

## 依赖

使用方需要自己的 KSP 插件，版本与工程的 Kotlin 编译器一致。本库不传递 `kotlin-stdlib`。

```kotlin
plugins {
    id("com.google.devtools.ksp") version "<与 Kotlin 匹配的版本>"
}

dependencies {
    implementation("io.github.pangli:model-annotation:1.0.1")
    ksp("io.github.pangli:model-compiler:1.0.1")
}
```

本仓库的 `:app` 直接依赖模块：

```kotlin
implementation(project(":model-annotation"))
ksp(project(":model-compiler"))
```

## 全局参数

写在使用方的 `ksp { }` 里。单个规格上的同名参数会覆盖它们。注解参数没写时走这里；显式传入空字符串会覆盖全局值，表示不使用该配置。

```kotlin
ksp {
    arg("model.classPrefix", "Sparrow")
    arg("model.classSuffix", "")
    arg("model.paramPrefix", "")
    arg("model.paramSuffix", "BySparrow")
    arg("model.packageName", "")
    arg("model.superClass", "")
    arg("model.superArgs", "")
    arg("model.nameRule", "base64") // raw、base64、reverse、xor、dict
    arg("model.nameEncoder", "")    // 编码器的全限定类名
    arg("model.xorKey", "bd")
    arg("model.dict", layout.projectDirectory.file("wire-names.properties").asFile.absolutePath)
    // 建议写绝对路径到 build/ 下，避免对照表被打进 APK；true 会进 generated resources
    arg(
        "model.mappingFile",
        layout.buildDirectory.file("outputs/model-wire-mapping.json").get().asFile.absolutePath,
    )
}
```

`model.dict` 指向的文件需要作为 KSP 任务输入，否则改字典后不会重新生成：

```kotlin
tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
    inputs.file(layout.projectDirectory.file("wire-names.properties"))
}
```

| 参数 | 作用 |
| --- | --- |
| `model.classPrefix` / `model.classSuffix` | 生成类名 = 前缀 + 规格简单名 + 后缀 |
| `model.paramPrefix` / `model.paramSuffix` | 生成参数名。前缀非空时，语义名首字母大写 |
| `model.packageName` | 生成类所在包。空字符串表示与规格同一个包 |
| `model.superClass` / `model.superArgs` | 规格没有写父类时使用。`superArgs` 原样放进父类构造括号 |
| `model.nameRule` | 默认算法。没配时等价于 `raw` |
| `model.nameEncoder` | 全局自定义算法的全限定类名 |
| `model.xorKey` | `xor` 使用的密钥 |
| `model.dict` | `dict` 使用的 properties 文件绝对路径 |
| `model.mappingFile` | 编译结束写出语义名 ↔ `@SerializedName` 对照 JSON。建议给 `build/` 下绝对路径（不会进 APK）。`true` 写到 `build/generated/ksp/<variant>/resources/model-wire-mapping.json`（可能被打包，仅调试用）；空或 `false` 关闭 |

## 编写规格

规格用抽象类或接口。父类、`val` / `var` 和 `=` 后面的默认值按源码复制到生成类。

```kotlin
@GenModel(classSuffix = "Wire")
abstract class DemoLogin(
    var phone: String,
    var children: List<DemoChild>? = null,
    @WireField(raw = "clientTag")
    val tag: String? = null,
    val clientName: String = someExpression(),
) : BaseRequest()
```

在 `classPrefix = Sparrow`、`paramSuffix = BySparrow`、`nameRule = base64` 时，生成：

```kotlin
data class SparrowDemoLoginWire(
    @SerializedName("cGhvbmU")
    var phoneBySparrow: String, // phone
    @SerializedName("Y2hpbGRyZW4")
    var childrenBySparrow: List<SparrowDemoChildWire>? = null, // children
    @SerializedName("clientTag")
    val tagBySparrow: String? = null, // tag
    @SerializedName("Y2xpZW50TmFtZQ")
    val clientNameBySparrow: String = someExpression(), // clientName
) : BaseRequest()
```

生成文件在 `app/build/generated/ksp/<variant>/kotlin/` 下，与规格同一个包（除非设置了 `model.packageName`）。嵌套规格使用短名和 `import`，不写全限定名。

开启 `model.mappingFile` 后，还会生成一份对照 JSON（路径由该参数决定），结构如下：

```json
{
  "com.example.SparrowDemoLoginWire": {
    "source": "com.example.DemoLogin",
    "fields": [
      { "semantic": "phone", "param": "phoneBySparrow", "wire": "cGhvbmU" }
    ]
  }
}
```

`semantic` 是参与算法的语义名，`param` 是生成构造参数名，`wire` 是最终 `@SerializedName`。

可空且没有默认值的属性生成 `= null`。没有 `=`、也不可空、又不是构造参数默认值的属性，不生成默认值。

`val` / `var` 跟规格声明走。需要覆盖时写在属性上：

```kotlin
@WireField(mutable = WireMutability.VAR)
val forcedVar: String? = null
```

`WireMutability.FOLLOW` 跟声明，`VAL` 强制 `val`，`VAR` 强制 `var`。

`@WireField` 还可以改单个属性：

| 参数 | 作用 |
| --- | --- |
| `key` | 参与算法的语义名。空字符串表示用属性名 |
| `raw` | 非空时直接作为 `@SerializedName`，跳过算法和编码器 |
| `defaultCode` | 规格上没有 `=` 时，用这段 Kotlin 表达式作为默认值 |
| `mutable` | 见上 |

生成参数名不能与父类已有成员同名，否则 KSP 报错。

## SerializedName 算法

参与计算的是语义名（属性名或 `WireField.key`），不是加过前后缀的参数名。

| 规则 | 结果 |
| --- | --- |
| `raw` | 语义名原样 |
| `base64` | URL-safe Base64，去掉填充。`phone` → `cGhvbmU` |
| `reverse` | 反转语义名 |
| `xor` | UTF-8 字节与 `model.xorKey` 循环异或，输出小写十六进制。密钥不能为空 |
| `dict` | 查 `model.dict`。缺键则报错 |

字典文件示例：

```properties
phone=cowsunflower
triggerPoint=foxoak
```

```kotlin
@GenModel(classSuffix = "Dict", nameRule = SerializedNameRule.DICT)
abstract class PhoneBook(
    val phone: String,
    val triggerPoint: String,
)
```

选择顺序：

1. `WireField.raw` 有值时直接使用。
2. 规格上的 `nameEncoder`。这时该规格的 `nameRule` 不再生效。
3. 规格上显式写的 `nameRule`（不是默认的 `OPTION`）。`DICT` 因此不会被全局编码器盖掉。
4. 全局 `model.nameEncoder`。
5. 全局 `model.nameRule`。

## 自定义算法

实现 `SerializedNameEncoder`，类要有公开的无参构造。编码器放在独立模块里，同时加入 `ksp` 依赖。写在 app 源码里时，KSP 加载不到它。

```kotlin
class PrefixNameEncoder : SerializedNameEncoder {
    override fun encode(semantic: String): String = "k_$semantic"
}
```

```kotlin
dependencies {
    compileOnly(project(":model-encoder"))
    ksp(project(":model-encoder"))
}
```

只给一个规格使用：

```kotlin
@GenModel(classSuffix = "Wire", nameEncoder = PrefixNameEncoder::class)
abstract class CustomBook(
    val phone: String,
    @WireField(raw = "fixedKey")
    val tag: String? = null,
)
```

`phone` 生成 `@SerializedName("k_phone")`，`tag` 仍是 `"fixedKey"`。

所有没单独指定算法的规格都用它时，写：

```kotlin
arg("model.nameEncoder", "com.example.PrefixNameEncoder")
```

`encode` 抛异常或返回空字符串时，该规格生成失败。

## API 路径常量

对 `object` 上的 `const val String` 做编译期路径混淆，用法与 `@GenModel` 对称。

```kotlin
@GenApiConstants
object ApiConstants {
    const val GET_APP_CONFIG_PATH: String = "api/app/ext/config/getApp"

    @ApiPath(raw = "healthz")
    const val HEALTH_PATH: String = "api/health"
}
```

在 `model.classPrefix = Sparrow`、`path.nameRule = dict` 且字典含各 path 段时，生成：

```kotlin
object SparrowApiConstants {
    const val GET_APP_CONFIG_PATH: String = "e/squill/nance/yew/lentil" // api/app/ext/config/getApp
    const val HEALTH_PATH: String = "healthz" // api/health
}
```

默认按 `/` **分段**编码（`path.encodeMode=segment`）；`whole` 则对整条路径一次编码。空段（首尾 `/`、`//`）会保留。

| 参数 | 作用 |
| --- | --- |
| `path.classPrefix` / `path.classSuffix` / `path.packageName` | 生成 object 名与包。未配置时回退到对应 `model.*` |
| `path.nameRule` | `raw` / `base64` / `reverse` / `xor` / `dict`，默认 `raw` |
| `path.nameEncoder` | 自定义算法全限定类名（与模型共用 `SerializedNameEncoder`） |
| `path.xorKey` | 未配时回退 `model.xorKey` |
| `path.dict` | `dict` 用的 properties（键是 path 段或整段，取决于 encodeMode） |
| `path.mappingFile` | 对照 JSON，建议写到 `build/` 绝对路径 |
| `path.encodeMode` | `segment`（默认）或 `whole` |

选择顺序与模型侧相同：`ApiPath.raw` → 规格 `pathEncoder` → 规格显式 `pathRule` → 全局 `path.nameEncoder` → 全局 `path.nameRule`。

对照 JSON 示例（`path.mappingFile`）：

```json
{
  "com.example.SparrowApiConstants": {
    "source": "com.example.ApiConstants",
    "paths": [
      { "name": "GET_APP_CONFIG_PATH", "semantic": "api/app/ext/config/getApp", "wire": "e/squill/nance/yew/lentil" }
    ]
  }
}
```

## 本仓库示例

规格在 `app/src/main/java/com/gson/model/generator/model/WireModelSpec.kt` 与 `ApiConstants.kt`。全局参数在 `app/build.gradle.kts`。字典在 `app/wire-names.properties` 与 `app/path-names.properties`。示例编码器是 `:model-encoder` 的 `PrefixNameEncoder`。

## 限制

- 规格不支持泛型。
- 规格源码里的星号 import 不会复制到生成文件。默认值表达式里用到的类型，需要在规格文件中有显式 import。
- 生成类里的参数名如果挡住父类成员，会报错。
- 同一简单类名冲突时，后出现的类型会保留全限定名。
- `@SerializedName` 的值在生成后就是普通字符串，R8 不会改写注解里的值。
- `@GenApiConstants` 只支持 `object` 里的 `const val String = "..."` 字面量，不支持引用其它常量。
