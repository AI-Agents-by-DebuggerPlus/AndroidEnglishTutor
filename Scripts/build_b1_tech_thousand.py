"""B1 topic: 1000 words on technology, engineering, AI, and programming."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(__file__).with_name("b1_tech_1000_words.tsv")
OUT = ROOT / "app" / "src" / "main" / "assets" / "topics" / "B1 Tech" / "b1_tech_thousand.json"

KEEP = {"ci", "key", "api", "gpu", "cpu", "ram", "rag", "sdk", "cli", "ide", "dns", "tls"}
DROP = {
    "shortcutkey", "branchname", "clientapp", "keyfield", "backupplan", "monitorjob",
    "clientnode", "imagefile", "secretstore", "issueboard", "estimatepoint", "rootcause",
    "messagebus", "promiseobj", "callbackhell", "tokenstream", "testcase", "featurecol",
    "sampledata", "labeldata", "metricscore", "accuracyrate", "precisionrate", "learningrate",
    "fewshot", "zeroshot", "tokenlimit", "contextwindow", "styletext", "adapterlora",
    "batchinfer", "cacheprompt", "loadbalance", "memoryagent", "retryloop", "handoffagent",
    "approvalgate", "policyagent", "browsertool", "searchtool", "codeexec", "artifactout",
    "logagent", "taskset", "successrate", "failuremode", "timeoutagent", "budgettoken",
    "stopcondition", "goalagent", "planstep", "embedindex", "constraintuser", "diffreview",
    "commentreview", "requestchange", "mergequeue", "greenbuild", "redbuild", "envvar",
    "portlocal", "consolelog", "watchvar", "stepover", "stepinto", "stackframe",
    "exceptionmsg", "patchfile", "commitmsg", "branchfeat", "releasecut", "changelogitem",
    "migrationnote", "sourcemap", "treeshake", "errortext", "prototypefast", "contextfile",
    "rulefile", "agentmode", "planmode", "askmode", "diffview", "applyedit", "terminalrun",
    "testcommand", "buildcommand", "lintcommand", "formatcommand", "logread", "errorloop",
    "smallstep", "demoapp", "testdraft", "explaincode", "workflowfile", "badprompt",
    "goodprompt", "outputformat", "jsonmode", "schemaout", "toolschema", "resulttool",
    "confirmstep", "safechange", "rollbackplan", "budgetcost", "primarymodel", "smallmodel",
    "largemodel", "localmodel", "cloudmodel", "closedmodel", "benchmarkscore", "sdkupdate",
    "examplecode", "sandboxrun", "envlocal", "dependencyupdate", "patchsec", "secretleak",
    "permissionless", "incidentresp", "backuptest", "retentionpolicy", "encryptrest",
    "certificateexp", "dnsrecord", "cacheheader", "errorbudget", "pageoncall", "actionitem",
    "metricnorth", "jobstory", "onlinework", "offlinework", "onlineinfer", "offlineinfer",
    "promptchain", "agentloop", "tooluse", "memorylong", "memoryshort", "contextpack",
    "filetree", "skilldoc", "summarychat", "nextstep", "demoresult", "examplepair",
    "reviewcard", "batchcard", "progressbar", "syncphone", "desktopapp", "sessionstudy",
    "goalstudy", "buildskill", "shipsmall", "askclear", "showwork", "checkresult",
    "keepsimple", "writeplain", "nameclear", "testfirst", "readerror", "fixone", "tryagain",
    "askhelp", "sharecontext", "saveprogress", "planfirst", "thenbuild", "thenreview",
    "thenship", "roleaccess", "statuscode", "hotfixbranch", "eventlog", "opensource",
    "repositoryurl", "bytepair", "vibecoding",
}

EXTRA = [
    ("reliable", "надёжный"),
    ("scaffold", "каркас"),
    ("chatbot", "чат-бот"),
    ("copilot", "второй пилот"),
    ("algorithm", "алгоритм"),
]

PHRASES = [
    ("a clear prompt", "ясный промпт", ["prompt", "clear"]),
    ("an AI agent", "ИИ-агент", ["agent"]),
    ("vibe coding", "вайбкодинг", ["vibe", "coding"]),
    ("write the code", "написать код", ["code"]),
    ("fix the bug", "исправить ошибку", ["bug"]),
    ("run the test", "запустить тест", ["test"]),
    ("deploy the service", "развернуть сервис", ["deploy", "service"]),
    ("read the log", "прочитать журнал", ["log"]),
    ("train the model", "обучить модель", ["train", "model"]),
    ("a code review", "ревью кода", ["code", "review"]),
    ("a simple prototype", "простой прототип", ["simple", "prototype"]),
    ("a public API", "публичный интерфейс", ["public", "api"]),
    ("save the file", "сохранить файл", ["save", "file"]),
    ("open the terminal", "открыть терминал", ["terminal"]),
    ("an engineering drawing", "инженерный чертёж", ["drawing"]),
    ("a hardware sensor", "аппаратный датчик", ["hardware", "sensor"]),
    ("a backup copy", "резервная копия", ["backup"]),
    ("user feedback", "отзыв пользователя", ["user", "feedback"]),
    ("the error message", "сообщение об ошибке", ["error", "message"]),
    ("a slow query", "медленный запрос", ["slow", "query"]),
    ("a fast prototype", "быстрый прототип", ["fast", "prototype"]),
    ("a merge conflict", "конфликт слияния", ["merge", "conflict"]),
    ("a language model", "языковая модель", ["language", "model"]),
    ("a system prompt", "системный промпт", ["system", "prompt"]),
    ("human approval", "одобрение человека", ["approval"]),
    ("a short plan", "короткий план", ["plan"]),
    ("the next step", "следующий шаг", ["step"]),
    ("read the documentation", "прочитать документацию", ["documentation"]),
    ("install the package", "установить пакет", ["install", "package"]),
    ("update the software", "обновить программу", ["update", "software"]),
    ("a neural network", "нейронная сеть", ["neuron", "network"]),
    ("a reliable system", "надёжная система", ["reliable", "system"]),
    ("a simple algorithm", "простой алгоритм", ["simple", "algorithm"]),
    ("the copilot suggestion", "подсказка второго пилота", ["copilot"]),
    ("a chatbot answer", "ответ чат-бота", ["chatbot", "answer"]),
    ("electric current", "электрический ток", ["current"]),
    ("a safety check", "проверка безопасности", ["safety"]),
    ("the main goal", "главная цель", ["goal"]),
    ("a clean dataset", "чистый набор данных", ["dataset"]),
    ("search the source", "найти источник", ["search", "source"]),
]

SENTENCES = [
    ("The agent reads the task and writes the code.", "Агент читает задачу и пишет код.", ["agent", "task", "code"]),
    ("I asked the model to fix the bug.", "Я попросил модель исправить ошибку.", ["model", "bug"]),
    ("Vibe coding is fast, but we still test the program.", "Вайбкодинг быстрый, но программу мы всё равно тестируем.", ["vibe", "coding", "fast", "test", "program"]),
    ("The engineer checked the circuit before the test.", "Инженер проверил схему перед испытанием.", ["engineer", "circuit", "test"]),
    ("Please write a clear prompt and give one example.", "Напиши ясный промпт и дай один пример.", ["clear", "prompt", "example"]),
    ("We deploy the service after the review.", "Мы разворачиваем сервис после проверки.", ["deploy", "service", "review"]),
    ("If the build fails, read the log and fix the error.", "Если сборка падает, прочитай журнал и исправь ошибку.", ["build", "log", "error"]),
    ("The team agreed on a simple design.", "Команда согласовала простой проект.", ["team", "simple", "design"]),
    ("A copilot can suggest code, but you decide.", "Второй пилот может предложить код, но решаешь ты.", ["copilot", "code"]),
    ("Do not put the secret in the file.", "Не клади секрет в файл.", ["secret", "file"]),
    ("The server was slow, so we checked the database.", "Сервер был медленным, поэтому мы проверили базу данных.", ["server", "slow", "database"]),
    ("I made a small prototype on my laptop.", "Я сделал маленький прототип на ноутбуке.", ["prototype", "laptop"]),
    ("The model can hallucinate, so check the source.", "Модель может галлюцинировать, поэтому проверь источник.", ["model", "hallucination", "source"]),
    ("An agent should ask before it deletes a file.", "Агент должен спросить, прежде чем удалить файл.", ["agent", "file"]),
    ("We train the model on a clean dataset.", "Мы обучаем модель на чистом наборе данных.", ["train", "model", "dataset"]),
    ("The API returns a short response.", "Интерфейс возвращает короткий ответ.", ["api", "response"]),
    ("Good code is simple and readable.", "Хороший код простой и читаемый.", ["code", "simple", "readable"]),
    ("I practice coding every day with a clear goal.", "Я практикуюсь в коде каждый день с ясной целью.", ["coding", "clear", "goal"]),
    ("The technician replaced the battery and the cable.", "Техник заменил батарею и кабель.", ["technician", "battery", "cable"]),
    ("The robot uses a sensor to measure the temperature.", "Робот использует датчик, чтобы измерить температуру.", ["robot", "sensor", "temperature"]),
    ("A chatbot is a simple kind of agent.", "Чат-бот — простой вид агента.", ["chatbot", "simple", "agent"]),
    ("This algorithm is reliable, but it is not fast.", "Этот алгоритм надёжный, но не быстрый.", ["algorithm", "reliable", "fast"]),
    ("We keep the documentation next to the code.", "Мы держим документацию рядом с кодом.", ["documentation", "code"]),
    ("The test showed an issue in the design.", "Испытание показало проблему в проекте.", ["test", "issue", "design"]),
    ("Ask the agent for a plan, then review the change.", "Попроси агента составить план, потом проверь изменение.", ["agent", "plan", "review", "change"]),
    ("A scaffold helps you start the design.", "Каркас помогает начать проект.", ["scaffold", "design"]),
    ("The network was offline, so the update failed.", "Сеть была недоступна, поэтому обновление не удалось.", ["network", "offline", "update"]),
    ("Write a test before you change the function.", "Напиши тест, прежде чем менять функцию.", ["test", "change", "function"]),
    ("The prompt needs more context.", "Промпту нужно больше контекста.", ["prompt", "context"]),
    ("Hardware and software must work together.", "Оборудование и программа должны работать вместе.", ["hardware", "software"]),
]


def load_words() -> list[dict]:
    seen: set[str] = set()
    words: list[dict] = []
    for line in SRC.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        en, ru = line.split("\t", 1)
        key = en.strip().casefold()
        if key in seen or key in DROP:
            continue
        if key not in KEEP and (key.endswith("verb") or key.endswith("ui") or key.endswith("ai")):
            continue
        seen.add(key)
        words.append({"en": en.strip(), "ru": ru.strip()})
    for en, ru in EXTRA:
        key = en.casefold()
        if key in seen:
            raise SystemExit(f"extra already present: {en}")
        seen.add(key)
        words.append({"en": en, "ru": ru})
    if len(words) != 1000:
        raise SystemExit(f"expected 1000 words, got {len(words)}")
    return words


def cards(rows, known: set[str]) -> list[dict]:
    out = []
    errors = []
    for en, ru, uses in rows:
        missing = [u for u in uses if u.casefold() not in known]
        if missing:
            errors.append(f"{en!r} uses unknown {missing}")
        out.append({"en": en, "ru": ru, "uses": uses})
    if errors:
        raise SystemExit("\n".join(errors))
    return out


def main() -> None:
    words = load_words()
    known = {w["en"].casefold() for w in words}
    payload = {
        "id": "b1_tech_thousand",
        "titleRu": "B1: техника, ИИ и код",
        "words": words,
        "phrases": cards(PHRASES, known),
        "sentences": cards(SENTENCES, known),
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"words={len(words)} phrases={len(PHRASES)} sentences={len(SENTENCES)} -> {OUT}")


if __name__ == "__main__":
    main()
