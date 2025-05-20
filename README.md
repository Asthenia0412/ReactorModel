# 单线程模型逻辑框架整理

## 整体架构

这是一个基于 Reactor 模式的单线程 Java NIO 服务器实现，主要处理 Echo 服务（接收客户端消息并原样返回）。

## 核心组件

### 1. Reactor 类

- **职责**：事件循环和分发中心
- 主要功能：
  - 初始化 Selector 和 ServerSocketChannel
  - 监听并分发事件
  - 管理 Acceptor 和 Handler

### 2. Acceptor 内部类

- **职责**：处理新连接
- 主要功能：
  - 接受新连接
  - 为每个新连接创建 EchoHandler

### 3. EchoHandler 类

- **职责**：处理已建立的连接
- 主要功能：
  - 读取客户端数据
  - 处理 Echo 逻辑（原样返回数据）
  - 管理连接状态

## 工作流程

1. **初始化阶段**：
   - 创建 Reactor 线程并启动
   - Reactor 初始化 Selector 和 ServerSocketChannel
   - 注册 OP_ACCEPT 事件并附加 Acceptor
2. **事件循环**：
   - Reactor 线程持续执行 select() 等待事件
   - 当有事件发生时，遍历 selectedKeys 并分发事件
3. **连接处理**：
   - 当有 OP_ACCEPT 事件时，调用 Acceptor.run()
   - Acceptor 接受新连接并创建 EchoHandler
   - EchoHandler 注册 OP_READ 事件
4. **数据交互**：
   - 当有 OP_READ 事件时，调用 EchoHandler.run()
   - 读取数据并原样返回
   - 重置缓冲区并重新注册 OP_READ 事件

## 代码结构

```
EchoServer
├── main() - 启动服务器
└── Reactor (implements Runnable)
    ├── 构造函数 - 初始化 NIO 组件
    ├── run() - 事件循环
    ├── dispatch() - 事件分发
    └── Acceptor (内部类, implements Runnable)
        └── run() - 处理新连接
└── EchoHandler (implements Runnable)
    ├── 构造函数 - 初始化连接处理
    └── run() - 处理数据读写
```

# MultiThreadEchoServer 逻辑框架整理

## 整体架构

这是一个基于多线程 Reactor 模式的 Java NIO 服务器实现，相比单线程版本，它通过主从 Reactor 模式和工作线程池实现了更好的性能扩展。

## 核心组件

### 1. MainReactor 类

- **职责**：主事件循环，专门处理连接请求

- 主要功能：

  - 初始化主 Selector 和 ServerSocketChannel
  - 创建并管理一组 SubReactor
  - 使用轮询算法分配新连接到 SubReactor

### 2. SubReactor 类

- **职责**：从事件循环，处理已建立连接的 I/O 事件

- 主要功能：

  - 每个 SubReactor 运行在独立线程中
  - 管理自己的 Selector 和事件循环
  - 使用工作线程池处理业务逻辑

### 3. Handler 类

- **职责**：处理单个连接的完整生命周期

- 主要功能：

  - 状态管理（READING, PROCESSING, SENDING）
  - 数据读写操作
  - 使用工作线程池处理业务逻辑

### 4. Processer 内部类

- **职责**：在工作线程中执行业务处理

- 主要功能：

  - 模拟业务处理（当前只是 sleep）
- 触发写操作

## 工作流程

1. **初始化阶段**：
   - 创建 MainReactor 线程并启动
   - MainReactor 初始化主 Selector 和 ServerSocketChannel
   - 根据 CPU 核心数创建多个 SubReactor 并启动
   - 每个 SubReactor 初始化自己的工作线程池
2. **连接处理**：
   - MainReactor 接受新连接
   - 使用轮询算法将新连接分配给 SubReactor
   - SubReactor 为连接创建 Handler 并注册 OP_READ 事件
3. **数据交互**：
   - SubReactor 检测到 OP_READ 事件，调用 Handler.read()
   - 读取数据后，将业务处理提交到工作线程池
   - 工作线程完成处理后，注册 OP_WRITE 事件
   - SubReactor 检测到 OP_WRITE 事件，调用 Handler.send()
   - 发送完成后，重新注册 OP_READ 事件

## 代码结构

```
MultiThreadEchoServer
├── main() - 启动服务器
└── MainReactor (implements Runnable)
    ├── 构造函数 - 初始化主 NIO 组件和 SubReactors
    ├── run() - 主事件循环
    ├── dispatch() - 事件分发
    └── Acceptor (内部类, implements Runnable)
        └── run() - 处理新连接并分配到 SubReactor
└── SubReactor (implements Runnable)
    ├── 构造函数 - 初始化子 Selector 和线程池
    ├── register() - 注册新连接
    ├── stop() - 停止子 Reactor
    └── run() - 子事件循环
└── Handler (implements Runnable)
    ├── 构造函数 - 初始化连接处理
    ├── run() - 根据状态处理 I/O
    ├── read() - 读取数据
    ├── send() - 发送数据
    ├── close() - 关闭连接
    └── Processer (内部类, implements Runnable)
        └── run() - 在工作线程中处理业务
```

## 改进点

1. **主从 Reactor 分离**：
   - 主 Reactor 专门处理连接请求
   - 从 Reactor 处理 I/O 操作，避免连接处理影响 I/O 性能
2. **多线程处理**：
   - 多个 SubReactor 并行处理 I/O
   - 工作线程池处理业务逻辑，避免阻塞 I/O 线程
3. **状态管理**：
   - 明确的 READ-PROCESS-SEND 状态机
   - 业务处理与 I/O 操作分离
4. **资源管理**：
   - 优雅关闭机制
   - 资源释放处理

## 潜在优化方向

1. 工作线程池大小可配置
2. 添加连接限流机制
3. 实现更复杂的业务处理逻辑
4. 添加监控和统计功能
5. 优化 SubReactor 分配策略（如基于负载）
