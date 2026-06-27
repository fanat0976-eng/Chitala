# Читалка

Универсальный читатель файлов для Android.

## Возможности

- **Текстовые файлы**: TXT, MD, CSV, JSON, XML, YAML
- **Документы**: DOCX (Apache POI), XLSX (Apache POI)
- **PDF**: просмотр страниц с навигацией
- **Тёмная тема**: переключение в шапке
- **Масштаб шрифта**: + / - кнопки
- **Поиск по тексту**: Ctrl+F аналог
- **История файлов**: последние 20 файлов
- **Поделиться**: отправка содержимого файла

## Технологии

- Kotlin + Jetpack Compose
- Material Design 3
- Apache POI (DOCX, XLSX)
- Android PdfRenderer (PDF)
- SharedPreferences (настройки)

## Установка

1. Скачайте APK из Releases
2. Установите на устройство
3. Откройте и выберите файл

## Сборка

```bash
# Из Android Studio
./gradlew assembleDebug

# APK будет в:
# app/build/outputs/apk/debug/app-debug.apk
```

## Структура

```
app/src/main/java/com/chitala/
├── reader/
│   ├── ChitalaApp.kt        — Application (MultiDex)
│   ├── MainActivity.kt      — точка входа
│   ├── FileReader.kt        — чтение файлов
│   └── PrefsManager.kt      — настройки и история
└── ui/
    ├── Theme.kt              — Material Design 3
    ├── MainScreen.kt         — главный экран
    └── PdfViewer.kt          — просмотр PDF
```

## Лицензия

MIT
