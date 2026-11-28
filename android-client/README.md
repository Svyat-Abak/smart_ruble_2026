# SmartRuble Android
Стартовый клиент на Kotlin и Jetpack Compose. Вход, список с поиском и пагинацией, карточка контрагента и банковские счета работают через API Gateway.

Откройте папку в Android Studio. Нужны JDK 17 и Android SDK 35. Дождитесь Gradle Sync, запустите конфигурацию app на эмуляторе.
Из терминала с установленным SDK выполните `./gradlew assembleDebug`.

Debug API_URL в app/build.gradle.kts равен http://10.0.2.2:8080/api/v1/ — адрес компьютера из Android Emulator.
Для физического устройства задайте LAN-адрес компьютера. Для release замените https://api.example.com/api/v1/ адресом вашего HTTPS-шлюза. HTTP разрешён только в debug.

MainActivity содержит Compose-экраны. MainViewModel хранит состояние и управляет загрузкой.
data/SmartRubleApi выполняет запросы в Dispatchers.IO, обновляет JWT через refresh и сериализует запросы Mutex.
Пароль и токены хранятся только в памяти. После перезапуска потребуется вход. Сервер проверяет права независимо от интерфейса.

Расширяйте шаблон отдельными экранами создания и согласования. Контракт API взят из текущего billing-service.

Проверка в текущем окружении: Gradle Wrapper и конфигурация проекта успешно проходят ./gradlew help.
assembleDebug остановился на отсутствии Android SDK. Компиляция Kotlin и запуск на эмуляторе ещё не проверены.
После установки Android Studio задайте SDK через local.properties (sdk.dir=/путь/к/Android/sdk) или ANDROID_HOME.
