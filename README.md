# 📷 MultiCam — Android Client

![Android](https://img.shields.io/badge/Platform-Android%207.0%2B-green?logo=android)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue?logo=jetpackcompose)
![Min SDK](https://img.shields.io/badge/minSdk-24-orange)

Мобильное приложение для анализа изображений с помощью ИИ. Сфотографируй задачу, блюдо или предмет — получи мгновенный результат.

---

## ✨ Возможности

- **📐 Математика и физика** — пошаговое решение задач с LaTeX-формулами
- **🧪 Химия** — балансировка уравнений, стехиометрия, расчёты
- **🍽 Питание** — КБЖУ с анимированной круговой диаграммой
- **🔍 Поиск объектов** — распознавание предметов + ссылки на Wildberries, Ozon, AliExpress
- **📝 Текст** — OCR с поддержкой Markdown
- **📸 Изображения** — описание сцен и объектов
- **⭐ Избранное** — сохранение результатов с синхронизацией между устройствами
- **👤 Гостевой режим** — вход без регистрации с возможностью апгрейда аккаунта

---

## 🛠 Технологический стек

| Слой | Технология |
|------|-----------|
| UI | Jetpack Compose + Material 3 |
| Архитектура | MVVM (ViewModel + mutableStateOf) |
| Сеть | Retrofit 2 + OkHttp 3 |
| Изображения | Coil |
| Markdown + LaTeX | WebView + marked.js + KaTeX |
| Сериализация | Gson |
| Асинхронность | Kotlin Coroutines |
| Хранилище | SharedPreferences |

---

## 🚀 Сборка и запуск

### Требования

- Android Studio Hedgehog или новее
- JDK 21
- Android SDK 36

### Шаги

```bash
git clone https://github.com/your-username/multicam-android.git
cd multicam-android
```

Открыть в Android Studio → `File > Open` → выбрать папку проекта.

Запустить через `Run > Run 'app'` или:

```bash
./gradlew assembleDebug
```

### Настройка сервера

В файле `app/src/main/java/ru/hotdog/multicam/api/BackendApi.kt` изменить `baseUrl`:

```kotlin
private val retrofit = Retrofit.Builder()
    .baseUrl("https://your-backend-url.up.railway.app/")
    ...
```

---

## 📁 Структура проекта

```
app/src/main/java/ru/hotdog/multicam/
├── api/
│   ├── AuthApi.kt           # Retrofit-интерфейс аутентификации
│   ├── BackendApi.kt        # Retrofit-интерфейс основного API + OkHttp-клиент
│   └── dto/                 # Модели данных (OCRResponse, TokenPair, ...)
├── model/
│   └── FavoriteItem.kt      # Модель избранного + enum категорий
├── sevice/
│   └── getDeviceUuid.kt     # Получение уникального ID устройства
├── ui/
│   ├── component/
│   │   ├── FavoritesDrawer.kt    # Панель избранного
│   │   ├── MarkdownText.kt       # WebView с Markdown + LaTeX
│   │   ├── NutritionCard.kt      # Карточка КБЖУ с диаграммой
│   │   └── ProductLinksCard.kt   # Карточка ссылок на маркетплейсы
│   ├── screen/
│   │   ├── MainActivity.kt       # Точка входа, навигация между экранами
│   │   ├── MainScreen.kt         # Главный экран
│   │   ├── RegistrationScreen.kt # Экран входа/регистрации
│   │   ├── ImageViewModel.kt     # ViewModel анализа изображений
│   │   ├── FavoritesViewModel.kt # ViewModel избранного
│   │   └── RegistrationViewModel.kt # ViewModel аутентификации
│   └── theme/                    # Цвета, типографика, тема
└── util/
    └── ImageUtils.kt        # Сжатие изображений перед отправкой
```

---

## 🔐 Аутентификация

Приложение поддерживает три режима входа:

```
┌─────────────────────────────────────────────┐
│  Гость (UUID устройства)                    │
│    ↓  нажал "Регистрация"                   │
│  Апгрейд аккаунта (/auth/upgrade)           │
│    • Все лайки сохраняются                  │
│    • userId не меняется                     │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  Обычный пользователь                       │
│    • Регистрация (/auth/signup/save)        │
│    • Вход (/auth/signin)                    │
└─────────────────────────────────────────────┘
```

Токены хранятся в `SharedPreferences("auth")`. При 401-ответе OkHttp-Authenticator автоматически обновляет access-токен через refresh-токен.

---

## 📦 Избранное — архитектура

```
Пользователь лайкает результат
         │
         ▼
  Локальное обновление (мгновенно)
  SharedPreferences.save()
         │
         ▼ (фоновый поток)
  POST /api/save/like
         │
         ▼
  Получаем backendId
  Обновляем запись локально
```

При запуске приложения сначала показывается локальный кэш, затем в фоне происходит синхронизация с сервером.

---

## 🔧 Ключевые зависимости

```kotlin
// build.gradle.kts
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
implementation("io.coil-kt:coil-compose:2.6.0")
```

---

## 📋 Требования к устройству

- Android 7.0 (API 24) и выше
- Доступ в интернет
- Камера или галерея для выбора изображений

---
