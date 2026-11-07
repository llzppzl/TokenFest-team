# Wireframes

Low-fidelity sketches of the dashboard pages. Admin pages share a left sidebar.

## Login

```
+--------------------------------------------------+
|                    TokenFest                     |
|                                                  |
|   [ Admin ]  [ Team member ]        <- tabs      |
|                                                  |
|   Username  [______________]                     |
|   Password  [______________]                     |
|                                                  |
|   (Team member tab: API key [______________])    |
|                                                  |
|                  [  Log in  ]                    |
|   error message shown here                       |
+--------------------------------------------------+
```

## Admin layout + Overview

```
+------------+---------------------------------------------------+
| TokenFest  |  Overview                [Last 7 days v]          |
|            |                                                   |
| Overview   |  +---------+ +---------+ +---------+ +---------+  |
| Requests   |  | Cost    | |Requests | | Tokens  | | Errors  |  |
| Providers  |  | $12.40  | |  1,203  | |  3.1M   | |   4     |  |
| Keys       |  +---------+ +---------+ +---------+ +---------+  |
| Settings   |                                                   |
|            |  Cost per day             [by key | model | prov]  |
|            |  |      ▆                                         |
|            |  |  ▃   █  ▅                                      |
|            |  |▂ █ ▄ █  █  ▃                                   |
|            |  +-----------------------                         |
| admin      |                                                   |
| [Log out]  |  Top keys this month          Budget used         |
|            |  alice   $40.10 / $100   [████░░░░░░] 40%         |
|            |  bob     $85.00 / $100   [████████░░] 85%         |
+------------+---------------------------------------------------+
```

## Requests

```
+------------+---------------------------------------------------+
| (sidebar)  |  Requests                                         |
|            |  Key [all v] Model [_____] Status [all v] [Search]|
|            |                                                   |
|            |  Time      Key    Model     Provider  Tokens Cost  St |
|            |  10:42:01  alice  gpt-4o    openai    1.2k  $0.01 200|
|            |  10:41:50  bob    claude-.. anthropic 8.0k  $0.04 200|
|            |  10:41:12  bob    gpt-4o    openai    -     -     429|
|            |                              [< Prev] 1/20 [Next >]|
|            |                                                   |
|            |  Click a row -> side panel: status, attempts,     |
|            |  latency, error, request JSON, response JSON      |
+------------+---------------------------------------------------+
```

## Providers

```
+------------+---------------------------------------------------+
| (sidebar)  |  Providers                       [+ Add provider] |
|            |                                                   |
|            |  Name        Type       Priority  Models   On     |
|            |  openai      OpenAI     10        3        [x] Edit|
|            |  deepseek    OpenAI     20        2        [x] Edit|
|            |  anthropic   Anthropic  10        2        [x] Edit|
|            |                                                   |
|            |  Edit form: name, type, base URL, API key         |
|            |  (blank = keep), priority, enabled,               |
|            |  models table: name | input $/1M | output $/1M [+]|
+------------+---------------------------------------------------+
```

## Keys

```
+------------+---------------------------------------------------+
| (sidebar)  |  Keys                               [+ New key]   |
|            |                                                   |
|            |  Name   Key        Budget  Spent   RPM  On        |
|            |  alice  tf-a1b2…   $100    $40.10  60   [x]  Edit |
|            |  bob    tf-c3d4…   $100    $85.00  60   [x]  Edit |
|            |                                                   |
|            |  After "Create": dialog showing the full key once |
|            |  with a [Copy] button and a warning               |
+------------+---------------------------------------------------+
```

## Settings

```
+------------+---------------------------------------------------+
| (sidebar)  |  Settings                                         |
|            |  Alert thresholds (%)  [50, 80, 100]              |
|            |  Webhook URL           [https://hooks.slack...]   |
|            |  Alert emails          [lead@team.com]            |
|            |  [Save]  [Send test alert]                        |
|            |                                                   |
|            |  Recent alerts                                    |
|            |  2026-10-12 bob 80%  $80.20/$100  delivered       |
+------------+---------------------------------------------------+
```

## User dashboard (team member)

```
+--------------------------------------------------------------+
| TokenFest      alice (tf-a1b2…)                  [Log out]   |
|                                                              |
|  This month: $40.10 of $100      [████░░░░░░] 40%            |
|  Rate limit: 60 requests/minute                              |
|                                                              |
|  Cost per day (by model)          [Export CSV]               |
|  |  ▃ ▅ █ ▂                                                  |
|                                                              |
|  My recent requests                                          |
|  Time      Model     Tokens  Cost   Status                   |
|  10:42:01  gpt-4o    1.2k    $0.01  200                      |
+--------------------------------------------------------------+
```
