<div align="center">

<img src="./logo.png" alt="Bili Downloader" width="200">

<h1>Bili Downloader</h1>

<p>基于 Bilibili 官方 API 开发的纯净哔哩哔哩视频下载器。</p>

</div>

## 项目简介

Bili Downloader 是一款基于 Bilibili 官方 API 开发的纯净哔哩哔哩视频下载器，可以实现 Bilibili 视频的下载、合并、批量下载等功能。项目完全离线，无任何第三方 SDK ，也没有任何遥测。视频合成使用纯原生 Kotlin 实现合并，非常轻。

> 开发本项目的起因是Bilibili AS 越来越臃肿，专门下载视频的项目后来也无法正常下载视频，因此就有了本项目，不能说很轻，但是也不大。
>
> 本项目由AI 辅助开发，仅供学习交流使用，请勿用于商业用途。
>
> 请不要在BiliBili等大陆平台公开发布本项目

## 功能特性

- 支持 BV 号和番剧 EP 号解析
- 多清晰度选择
- 多P/多集批量下载
- 视频音频本地合并
- Material Design 3 界面设计

## 开发环境

- JDK 17
- Android SDK 34
- Gradle 8.12
- NDK 27.0.12077973

## 构建

```bash
git clone https://github.com/MakotoArai-CN/BiliDownloader.git
cd BiliDownloader

./gradlew assembleRelease
```

## 下载

| 平台 | 下载地址 | 描述 |
| --- | --- | --- |
|Android|[GitHub Releases](https://github.com/MakotoArai-CN/BiliDownloader/releases)| 本项目的编译版本 |
|Windows/macOS|[video-download-helper](https://github.com/MakotoArai-CN/video-download-helper)| 油猴脚本，本项目的前身 |

## 许可证

AGPL-3.0 License

## 反馈

如果您有任何反馈或建议，欢迎提交 [Issue](https://github.com/MakotoArai-CN/BiliDownloader/issues) 或 [Pull Request](https://github.com/MakotoArai-CN/BiliDownloader/pulls)，本项目如果对您有帮助到您，请给本项目点个 Star，谢谢。

## 鸣谢

- [Bilibili](https://www.bilibili.com/)
- [Kotlin](https://kotlinlang.org/)