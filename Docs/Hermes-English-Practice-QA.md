# Hermes English practice — Q&A by chat themes

Use with **EnglishTutor** / AndroidEnglishTutor / AHCC (`TO: english_tutor` or `@npch_english_tutor_bot`).

**How to study**
1. Copy a **You say** line into the chat (Russian or English — both work).
2. Read the **Agent (English)** answer aloud; check the **RU gloss** only if needed.
3. For cards: send `next` after practice starts; stop with `стоп` / `stop_flashcards`.
4. For offline packs in AET: use the **Generate topic** prompts at the end of each section.

Themes match AET assets: `general_conversation`, `food`, `tools`, `bottle_depot`, `trading`, `ai_engineering`, plus packed practice topic `hermes_english_practice` (this file → Word Study).

---

## 0. Tutor identity (no flashcards)

### You say
`Who are you?` / `Ты кто?`

### Agent (English)
I'm EnglishTutor — your flashcard coach. I help with English words, phrases, and short sentences, with Russian hints when useful. Say **next** when you want a card, or ask me to generate an offline topic pack.

### RU gloss
Я EnglishTutor — тренер по карточкам. Помогаю со словами, фразами и короткими предложениями, с русскими подсказками. Напиши **next**, когда нужна карточка, или попроси сгенерировать offline-тему.

---

## 1. General conversation (`general_conversation`)

### 1.1 You say
`How do I greet someone politely at work?`

### Agent (English)
Keep it short and warm: “Good morning — good to see you.” If you haven’t met: “Nice to meet you.” For a colleague you know: “Hey — how’s it going?” Avoid overlong small talk before the meeting starts.

### RU gloss
Коротко и тепло: «Good morning — good to see you.» При знакомстве: «Nice to meet you.» Коллеге: «Hey — how’s it going?» Не раздувай small talk до начала встречи.

---

### 1.2 You say
`I didn't catch what they said. What can I say?`

### Agent (English)
Useful lines: “Sorry — I didn’t catch that.” “Could you repeat that, please?” “Could you speak a little slower?” Then paraphrase: “So you mean … — is that right?”

### RU gloss
«Sorry — I didn’t catch that.» «Could you repeat that, please?» «Could you speak a little slower?» Потом перескажи: «So you mean … — is that right?»

---

### 1.3 You say
`Help me end a call politely.`

### Agent (English)
Try: “Thanks for your time — I’ll get back to you today.” Or: “Let’s stay in touch. Talk soon.” If you’re late for another meeting: “I have to run — catch you later.”

### RU gloss
«Thanks for your time — I’ll get back to you today.» «Let’s stay in touch. Talk soon.» Если спешишь: «I have to run — catch you later.»

---

### 1.4 You say
`Generate an offline topic pack for everyday conversation.`

### Agent (English)
```json
{"id":"general_conversation_practice","titleRu":"Общее общение (practice)","words":[{"en":"greet","ru":"приветствовать"},{"en":"catch","ru":"расслышать"},{"en":"repeat","ru":"повторить"},{"en":"clarify","ru":"уточнить"},{"en":"postpone","ru":"отложить"}],"phrases":[{"en":"nice to meet you","ru":"приятно познакомиться","uses":["meet"]},{"en":"I didn't catch that","ru":"я не расслышал","uses":["catch"]},{"en":"could you repeat that","ru":"не могли бы вы повторить","uses":["repeat"]}],"sentences":[{"en":"Could you speak a little slower, please?","ru":"Не могли бы вы говорить чуть медленнее?","uses":["speak"]},{"en":"I'll get back to you this afternoon.","ru":"Я вернусь к вам с ответом сегодня днём.","uses":["get"]}]}
```

*(In a real Hermes turn, expect a fuller pack: 12–24 words, 8–14 phrases, 6–12 sentences.)*

---

## 2. Food (`food`)

### 2.1 You say
`I'm at a café. How do I order coffee and ask for the bill?`

### Agent (English)
Order: “Hi — could I get a medium latte to go, please?” Allergies: “Does this contain dairy?” Bill: “Could we get the check, please?” Split: “Can we split the bill?” Paying: “I’ll take the card” / “Keep the change.”

### RU gloss
«Could I get a medium latte to go, please?» «Does this contain dairy?» «Could we get the check, please?» «Can we split the bill?» «Keep the change.»

---

### 2.2 You say
`How do I complain politely about cold food?`

### Agent (English)
Stay calm and specific: “Excuse me — my soup arrived cold. Could you warm it up, please?” Or: “This isn’t what I ordered. I asked for no onions.” End with thanks: “I appreciate it.”

### RU gloss
«Excuse me — my soup arrived cold. Could you warm it up, please?» «This isn’t what I ordered.» «I appreciate it.»

---

### 2.3 You say
`Сгенерируй offline-тему: еда и кафе`

### Agent (English)
Ask Hermes for a `TopicFileDto` JSON with id like `food_cafe`, titleRu «Еда / кафе», covering order / bill / dietary needs. Then save offline in AET Word Study.

---

## 3. Tools / hardware (`tools`)

### 3.1 You say
`I need to borrow a drill. What do I say in a workshop?`

### Agent (English)
“Do you mind if I borrow the drill for ten minutes?” Safety: “Where are the safety glasses?” Returning: “Thanks — I put it back on the shelf.” If broken: “The bit snapped — do you have a spare?”

### RU gloss
«Do you mind if I borrow the drill…?» «Where are the safety glasses?» «I put it back on the shelf.» «The bit snapped — do you have a spare?»

---

### 3.2 You say
`Explain tighten vs loosen in simple English.`

### Agent (English)
**Tighten** = make a screw or nut firmer (turn clockwise on most fasteners). **Loosen** = make it less tight (usually counter-clockwise). Tip: “Righty-tighty, lefty-loosey” — remember it, then verify on the tool.

### RU gloss
Tighten — затянуть. Loosen — ослабить. Подсказка Righty-tighty / lefty-loosey — потом всегда проверяй на месте.

---

## 4. Bottle Depot (`bottle_depot`)

### 4.1 You say
`What do I say when I return bottles in Canada?`

### Agent (English)
At the counter: “Hi — I’d like to return these empties.” Ask: “Do you take beer cans and wine bottles here?” If sorting: “Where should I put the clear glass?” Receipt: “Could I get a refund slip, please?”

### RU gloss
«I’d like to return these empties.» «Do you take beer cans and wine bottles here?» «Where should I put the clear glass?» «Could I get a refund slip, please?»

---

### 4.2 You say
`The machine jammed. Help me ask for help.`

### Agent (English)
“Excuse me — the reverse vending machine jammed. Can you help clear it?” Show the bag: “These are mostly plastic bottles.” Thanks: “Thanks for sorting that out.”

### RU gloss
«The reverse vending machine jammed. Can you help clear it?» «Thanks for sorting that out.»

---

## 5. Trading / markets (`trading`) — Hermes Trader chats

### 5.1 You say
`Explain open order vs trade history in plain English.`

### Agent (English)
An **open order** is an instruction that is still live — not filled yet (or only partly filled). **Trade history** shows deals that already happened — filled trades with price, size, and time. You can cancel an open order; you can’t “cancel” history.

### RU gloss
Open order — ещё живая заявка. Trade history — уже исполненные сделки. Заявку можно отменить; историю — нет.

---

### 5.2 You say
`How do I ask an agent to check my Binance Demo positions?`

### Agent (English)
Be concrete: “Please list my open USDT-M futures positions on Binance Demo, including symbol, side, size, entry, and unrealized PnL.” Add: “Don’t invent numbers — query the API or say if the tool failed.”

### RU gloss
Чёткий запрос: список позиций Demo, символ, сторона, размер, вход, PnL. «Не выдумывай цифры — дерни API или скажи, что tool упал.»

---

### 5.3 You say
`Translate: «разнесли по золоту» in a trading chat — what might people mean?`

### Agent (English)
In slang chat it often means “got wrecked / smashed on gold (XAU)” — large losses on gold-related trades. Ask for context: “Do you mean traders lost heavily on gold, or that profits were paid in gold?” Don’t guess account facts without data.

### RU gloss
Часто: «сильно убыло / размазало на золоте (XAU)». Уточни контекст; не выдумывай факты по счетам.

---

## 6. AI / engineering (`ai_engineering`) — Hermes / AHCC chats

### 6.1 You say
`Explain gateway restart in simple English.`

### Agent (English)
The **gateway** is the process that connects Telegram (or other channels) to Hermes. A **restart** stops that process and starts it again — open chats may show “Hermes is shutting down,” then recover. Your question might not finish if the restart happens mid-task.

### RU gloss
Gateway — мост Telegram↔Hermes. Restart — перезапуск; задача посередине может оборваться.

---

### 6.2 You say
`What is a profile bot in our multi-agent setup?`

### Agent (English)
Each Hermes **profile** (Operator, SysAdmin, EnglishTutor, …) can have its own Telegram bot. You message that bot directly — no `TO:` router. AHCC Desktop can watch several bots and switch the chat when a reply arrives.

### RU gloss
У каждого профиля свой бот; пишешь напрямую. Desktop может слушать несколько ботов и переключать чат.

---

### 6.3 You say
`Help me ask SysAdmin to free disk space — in English.`

### Agent (English)
“Please inspect disk usage on the Hermes host, list the largest folders under `/opt/data`, and propose a cleanup plan with sizes. Wait for my OK before deleting anything.”

### RU gloss
«Проверь диск, покажи самые большие папки, предложи план с размерами. Удаляй только после моего OK.»

---

## 7. Flashcard practice commands

| You say | Expected agent behavior |
|---------|-------------------------|
| `next` / `дальше` / `ещё` | `{"skill":"flashcard_start"}` (once) then `{"type":"flashcard","en":"…","ru":"…"}` |
| `стоп` / `stop_flashcards` / `хватит` | `{"skill":"flashcard_stop"}` |
| `Who are you?` | Plain text only — **no** flashcard JSON |

### Example after `next`

**Agent**
```text
{"skill":"flashcard_start"}
{"type":"flashcard","en":"refund","ru":"возврат денег"}
```

---

## 8. Quick drill sheet (say → read)

Copy left column; practice reading the right column aloud.

| You say (RU/EN) | Read aloud (EN) |
|-----------------|-----------------|
| Как дела? | How’s it going? |
| Повторите, пожалуйста | Could you repeat that, please? |
| Счёт, пожалуйста | Could we get the check, please? |
| Я опаздываю | I’m running late. |
| Держи меня в курсе | Keep me posted. |
| Не выдумывай цифры | Don’t invent the numbers — query the API. |
| Перезапусти gateway | Please restart the Hermes gateway. |
| Кто ты? | Who are you? / What do you do? |

---

## Related

- AET topics: `app/src/main/assets/topics/**/*.json` (including `Hermes/hermes_english_practice.json` built from this guide)
- Hermes persona: NousPortalCloudHermes `Docs/Agents/EnglishTutor.SOUL.md`
- AHCC addressees: `Docs/Guides/AHCC-Addressees.md`
