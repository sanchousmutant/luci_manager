# OpenWrt Interface Manager

![OpenWrt Manager](https://img.shields.io/badge/OpenWrt-Manager-blue?style=flat-square&logo=openwrt)
![Android](https://img.shields.io/badge/Android-Kotlin-green?style=flat-square&logo=android)
![Material Design 3](https://img.shields.io/badge/Material%20Design-3.0-orange?style=flat-square&logo=material-design)

Современное Android-приложение для удобного управления сетевыми интерфейсами на роутере с OpenWrt через LuCI RPC API.

## ✨ Особенности

### 🎨 Современный дизайн
- **Material Design 3.0** - следует последним принципам дизайна Google
- **Темная и светлая темы** - автоматическое переключение
- **Интуитивно понятный интерфейс** - минималистичный и функциональный
- **Плавные анимации** и переходы между экранами

### 🔐 Безопасность
- **HTTPS поддержка** с обработкой self-signed сертификатов
- **Безопасная аутентификация** через LuCI RPC API
- **Управление сессиями** с автоматическим logout
- **Локальное сохранение** учетных данных (с возможностью шифрования)

### 🌐 Сетевые функции
- **Просмотр всех сетевых интерфейсов** (LAN, WAN, WWAN, WiFi)
- **Включение/отключение интерфейсов** одним касанием
- **Реальное время статусов** с автоматическим обновлением
- **Pull-to-refresh** для обновления данных
- **Детальная информация** об интерфейсах (IP, device, protocol)

## 📱 Скриншоты интерфейса

### Экран входа (Login Screen)
```
┌─────────────────────────────────────┐
│        🌐 OpenWrt Manager          │
│     Manage your network interfaces  │
│                                     │
│  ┌─────────────────────────────────┐ │
│  │ 🌐 Router IP Address           │ │
│  │ 192.168.1.1                   │ │
│  └─────────────────────────────────┘ │
│                                     │
│  ┌─────────────────────────────────┐ │
│  │ 👤 Username                    │ │
│  │ root                           │ │
│  └─────────────────────────────────┘ │
│                                     │
│  ┌─────────────────────────────────┐ │
│  │ 🔒 Password                    │ │
│  │ ••••••••                       │ │
│  └─────────────────────────────────┘ │
│                                     │
│  ┌─────────────────────────────────┐ │
│  │      🚀 Connect to Router      │ │
│  └─────────────────────────────────┘ │
│                                     │
│    Make sure your device is         │
│    connected to the same WiFi...    │
└─────────────────────────────────────┘
```

### Экран управления интерфейсами (Interface Management)
```
┌─────────────────────────────────────┐
│ Network Interfaces      🔄  🚪      │
├─────────────────────────────────────┤
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ 🌐  LAN                        │ │
│ │     ● Active                   │ │
│ │     192.168.1.1 • br-lan   [⏹] │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ 🌍  WAN                        │ │
│ │     ● Active                   │ │
│ │     192.168.0.100 • eth0   [⏹] │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ 📶  WWAN                       │ │
│ │     ○ Inactive                 │ │
│ │     wlan0                  [▶]  │ │
│ └─────────────────────────────────┘ │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ 📡  Guest WiFi                 │ │
│ │     ○ Inactive                 │ │
│ │     wlan1                  [▶]  │ │
│ └─────────────────────────────────┘ │
│                                     │
└─────────────────────────────────────┘
```

## 🏗️ Архитектура

### 📦 Структура проекта
```
com.example.lucimanager/
├── model/                    # Модели данных
│   ├── NetworkInterface.kt
│   ├── LoginCredentials.kt
│   └── LuciSession.kt
├── network/                  # Сетевой слой
│   └── LuciApiClient.kt     # HTTP клиент для LuCI API
├── repository/               # Репозиторий для данных
│   └── NetworkRepository.kt
├── viewmodel/               # ViewModels (MVVM)
│   ├── LoginViewModel.kt
│   └── InterfaceViewModel.kt
└── ui/                      # UI слой
    ├── login/
    │   └── LoginFragment.kt
    └── interfaces/
        ├── InterfacesFragment.kt
        └── NetworkInterfaceAdapter.kt
```

### 🔧 Используемые технологии

#### Core Android
- **Kotlin** - современный язык программирования
- **ViewBinding** - безопасная привязка views
- **Navigation Component** - навигация между экранами
- **Fragments** - модульная архитектура UI

#### Architecture Components
- **ViewModel** - управление состоянием UI
- **LiveData** - reactive обновления UI
- **Repository Pattern** - централизованное управление данными

#### Network
- **OkHttp** - HTTP клиент с поддержкой HTTPS
- **Gson** - JSON парсинг для API ответов
- **Coroutines** - асинхронная обработка

#### UI/UX
- **Material Design 3** - современные компоненты UI
- **RecyclerView** - эффективные списки
- **SwipeRefreshLayout** - pull-to-refresh
- **ConstraintLayout** - гибкие layouts

## 🚀 Начало работы

### Требования
- Android 14+ (API level 34+)
- Kotlin 1.9+
- Android Studio Hedgehog или новее

### Установка
1. Клонируйте репозиторий
2. Откройте проект в Android Studio
3. Синхронизируйте Gradle зависимости
4. Соберите и запустите проект

### Конфигурация OpenWrt
Убедитесь, что на вашем роутере OpenWrt:
- Включен LuCI веб-интерфейс
- Настроен RPC API доступ
- Ваше Android устройство подключено к той же сети

## 🔌 API Integration

### LuCI RPC Endpoints
```kotlin
// Аутентификация
POST /cgi-bin/luci/rpc/auth
{
  "method": "login",
  "params": ["username", "password"]
}

// Получение статуса интерфейсов
POST /cgi-bin/luci/rpc/sys
{
  "method": "call",
  "params": [token, "network", "get_status"]
}

// Управление интерфейсами
POST /cgi-bin/luci/rpc/sys  
{
  "method": "call",
  "params": [token, "network", "ifup/ifdown", {"interface": "lan"}]
}
```

## 🛠️ Разработка

### Добавление нового интерфейса
1. Обновите `NetworkInterface` модель
2. Добавьте логику в `LuciApiClient`
3. Обновите `NetworkInterfaceAdapter`
4. Добавьте соответствующие иконки и ресурсы

### Кастомизация UI
Все цвета и стили находятся в:
- `res/values/colors.xml` - цветовая палитра
- `res/values/themes.xml` - темы приложения
- `res/values/strings.xml` - текстовые ресурсы

## 🔒 Безопасность

### Рекомендации
- Используйте HTTPS для продакшен окружения
- Регулярно обновляйте пароли роутера
- Не сохраняйте чувствительные данные в plain text
- Рассмотрите использование Android Keystore для шифрования

## 🐛 Отладка

### Логи
Включите подробное логирование в `LuciApiClient` для отладки API вызовов:
```kotlin
private val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .build()
```

### Типичные проблемы
- **Ошибка подключения**: проверьте IP адрес и сетевое соединение
- **Ошибка аутентификации**: убедитесь в правильности логина/пароля
- **Timeout**: увеличьте timeout в OkHttp клиенте

## 📝 Лицензия

Этот проект распространяется под лицензией MIT. Подробности в файле `LICENSE`.

## 👥 Вклад в проект

Мы приветствуем вклад в развитие проекта! Пожалуйста:
1. Создайте fork репозитория
2. Создайте feature branch
3. Сделайте commit изменений
4. Создайте Pull Request

---

## 📞 Поддержка

Если у вас есть вопросы или предложения:
- Создайте Issue в GitHub
- Свяжитесь с командой разработки

**Сделано с ❤️ для OpenWrt сообщества**