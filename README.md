# Chat-Server

Chat-Server是聊天工具**公式炼成**的后端服务器。

## 使用API

使用API请参考[API文档](https://github.com/formula-forge/chat-server-doc/tree/main/docs/doc1).

## 部署

### 准备工作

要求JDK版本`>=17`.

编写配置文件, 置于`/etc/chat-server/config.yaml`或执行Java的同一目录下.

下载config-example.yaml

```bash
cp config-example.yaml /etc/chat-server/config.yaml
```

并根据实际服务器情况修改.

### 运行

```bash
java -jar chat-server.jar
```

### 连接[mathjax-server](https://github.com/formula-forge/mathjax-server)

```yaml
jax:
  enable: true
  host: 127.0.0.1
  port: 2023
```
