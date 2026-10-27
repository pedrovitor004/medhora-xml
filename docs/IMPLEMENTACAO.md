# Implementação MedHora XML

## Referência visual

Arquivo oficial: [MedHora no Figma](https://www.figma.com/design/EdN09F1Pg1vvbTcGIYLxEZ/MedHora---Aplicativo-com-m%C3%BAltiplas-telas?node-id=0-1).

A consulta encontrou as dez telas dentro de uma imagem única (nó 1:11), o logotipo separado (1:3) e as cinco amostras de cores (1:16, 1:17, 1:19, 1:21 e 1:23). As cores originais correspondem à paleta fornecida e foram centralizadas em `colors.xml`. A imagem das telas fica exclusivamente em `docs/prototipo-medhora.png`, como documentação; nenhuma tela do aplicativo é uma captura de tela.

O logotipo original está em `drawable-nodpi/medhora_logo.png`, sem alterações, e é usado em splash/onboarding e no ícone do aplicativo. A proporção original quadrada foi preservada com `fitCenter` nos layouts. Os ícones de interface são vetores locais, pois o Figma não disponibiliza os ícones individuais das telas. Textos, cinzas, bordas e estados são aproximações explicitamente identificadas em `colors.xml`.

## Fluxos e dados

- Splash → onboarding → início, retirando as boas-vindas da pilha de retorno.
- Navegação inferior entre início, histórico, adicionar, medicamentos e ajustes.
- Início com semana atual, seleção do dia, resumo e doses em ordem de horário. O conteúdo pode rolar em telas menores.
- Formulário compartilhado para cadastro e edição: nome, dose, frequência, cor, observações e até 12 horários únicos, com seletor de 24 horas.
- Campos obrigatórios e horários são validados; é possível adicionar, alterar e remover horários. O formulário oferece confirmação ao descartar alterações.
- Rascunho mantido em ViewModel durante a rotação, sem armazenamento permanente.
- Lista de medicamentos com busca sem distinção de acentos ou maiúsculas e acesso aos detalhes.
- Detalhes com frequência, horários, última dose, observações, edição e exclusão confirmada.
- Registro idempotente de dose e diálogo centralizado de sucesso com fundo escurecido.
- Histórico agrupado por data, com filtros Hoje, 7 dias e 30 dias e estados tomada, pendente e atrasada.
- Configurações com aparência clara, informações do aplicativo, ativação dos lembretes reais, escolha de som e acesso à permissão de horários exatos.

`MedicamentoRepository` é um singleton do processo e expõe um snapshot imutável via LiveData. Não usa Room, SQLite, Firebase, DataStore, SharedPreferences, rede ou arquivos de dados. A inicialização é vazia, sem medicamentos ou doses de exemplo. Os medicamentos, doses e a ativação dos lembretes desaparecem ao encerrar o processo; uma nova sessão volta a iniciar no splash. O Android mantém as preferências do próprio canal de notificações, incluindo o som escolhido, independentemente da memória do aplicativo.

A agenda é materializada até o dia atual e o início também permite consultar os próximos dias da semana. Datas anteriores ao cadastro não recebem doses. Uma atualização periódica, somente enquanto início/histórico estão visíveis, atualiza atrasos e a virada do dia. As frequências disponíveis são todos os dias, dias úteis e fins de semana.

Edições recalculam a agenda de hoje e dos próximos dias, preservando doses anteriores e doses tomadas. Registros mantêm um snapshot do nome e da dosagem; excluir o medicamento remove doses não tomadas, mas conserva os registros já feitos nesta sessão. O histórico mostra até 30 dias, sem produzir dados anteriores ao início da sessão/cadastro.

Datas e horas usam `java.time`, com core library desugaring para compatibilidade com minSdk 24. As dependências adicionadas são RecyclerView, Lifecycle LiveData/Runtime e desugaring, centralizadas no catálogo de versões. A sincronização dessas dependências não foi executada nesta entrega.

## Lembretes e som (atualização solicitada pelo usuário)

O usuário optou explicitamente por manter os dados temporários em memória. A restrição anterior sobre notificações reais foi substituída pela solicitação de implementar lembretes locais e escolha de som. Não foi adicionado Room nem outro armazenamento de medicamentos.

`MedHoraApplication` observa o repositório durante o processo inteiro. `AgendadorLembretes` programa a próxima ocorrência de cada horário com AlarmManager e PendingIntent imutável. Cadastro, edição, exclusão, registro de dose e ativação/desativação reconciliam os alarmes. A entrega é feita por `LembreteReceiver`, que verifica a sessão e a existência atual do medicamento, mostra a notificação e agenda a próxima ocorrência conforme a frequência. Não há timer que dependa de uma tela aberta para emitir o aviso.

As notificações usam o nome e a dosagem reais cadastrados: título “Hora de tomar [nome]”, corpo “Dose: [dosagem] · Horário: [HH:mm]”. Tocar abre o aplicativo. O aviso não marca a dose como tomada automaticamente. O conteúdo é privado na tela bloqueada, conforme as configurações de privacidade do celular. A personalização também é lida em voz alta quando a opção de voz está ativa, conforme a seção abaixo.

Em Android 13+, o app solicita POST_NOTIFICATIONS quando a pessoa ativa lembretes. Em Android 12+, Configurações oferece acesso especial para horários exatos. Sem acesso, é usado um alarme inexato e a interface explica que pode haver atraso. Revogação e mudanças de fuso/horário são consideradas na reconciliação. Restrições de bateria e do fabricante podem influenciar a entrega.

Em Android 8+, “Som das notificações” abre as configurações do canal “Horários dos medicamentos”, onde o usuário escolhe o som disponível ou silencioso. O aplicativo lê o nome do som escolhido ao voltar. Um canal estável preserva as escolhas do usuário, em vez de criar canais para contornar preferências. Em Android 7, o seletor nativo de sons mantém a escolha somente na memória da sessão. O aplicativo não inclui arquivos de áudio nem baixa sons.

Os lembretes podem aparecer com o app minimizado enquanto o processo da sessão continua existindo. Se o processo for encerrado, alarmes antigos são descartados ao serem recebidos, pois não existe mais o medicamento em memória. Reiniciar o aparelho também remove os alarmes. Não há restauração após boot, serviço permanente, banco de dados ou promessa de entrega depois do fim da sessão.

Para usar: cadastre seu medicamento, abra Ajustes, ative “Lembretes de medicamentos”, permita as notificações e configure o som. Se desejar precisão, permita horários exatos. Após salvar um medicamento com lembretes desativados, a mensagem de salvamento oferece o atalho “Ativar lembretes”.

Fontes oficiais consultadas: [agendamento de alarmes](https://developer.android.com/develop/background-work/services/alarms), [permissão de notificações](https://developer.android.com/develop/ui/compose/notifications/notification-permission) e [canais e sons](https://developer.android.com/reference/android/app/NotificationChannel).

Arquivos novos desta atualização:

- `app/src/main/java/com/example/medhoraxml/MedHoraApplication.kt`
- `app/src/main/java/com/example/medhoraxml/notifications/AgendadorLembretes.kt`
- `app/src/main/java/com/example/medhoraxml/notifications/LembreteReceiver.kt`
- `app/src/main/java/com/example/medhoraxml/notifications/Notificacoes.kt`
- `app/src/main/res/drawable/ic_notification.xml`

Arquivos alterados nesta atualização: `MainActivity.kt`, `MedicamentoRepository.kt`, `CadastroFragment.kt`, `ConfiguracoesFragment.kt`, `AndroidManifest.xml`, `fragment_configuracoes.xml`, `fragment_home.xml`, `strings.xml`, este documento e `README.md`.

Foi feita revisão estática dos XML e referências; nenhuma compilação, teste ou emulador foi executado pelo agente. A validação em aparelho permanece pendente.

## Aviso falado para acessibilidade

A pedido do usuário, os lembretes agora também podem falar: “Hora de tomar o medicamento [nome]. Dose: [dosagem]”. `TextoDaDose` expande abreviações de unidades conhecidas (`mg`, `mcg`, `g`, `ml`, `UI`) para a pronúncia, preservando os números e a unidade cadastrada. Não presume que toda dosagem seja em miligramas nem converte quantidades médicas.

`VozLembreteService` usa TextToSpeech do próprio celular, exige uma voz offline em português (preferindo pt-BR), usa velocidade 0,85 e atributos de áudio de alarme/fala. Nenhum serviço de voz externo, permissão de microfone ou arquivo de medicamento persistente foi adicionado. Se a voz não estiver disponível, a pessoa recebe uma mensagem e pode abrir “Configurar voz do celular”. O aplicativo não baixa vozes automaticamente.

O serviço só permanece em primeiro plano durante a inicialização/leitura, com notificação silenciosa de controle e ação “Parar voz”. Pedidos simultâneos entram em fila, sem sobrepor duas leituras; uma dose tomada, excluída ou alterada deixa de ser lida. A fala libera o foco de áudio e o motor ao terminar ou ser interrompida, e tem um limite de execução de segurança. Não reinicia automaticamente após o processo ser encerrado.

Em Ajustes foram adicionados:

- “Falar o lembrete em voz alta” (ativado por padrão nesta sessão, ainda dependente de ativar os lembretes).
- Controle do volume de alarme, com percentual. O usuário pode aumentar até o máximo permitido pelo aparelho. Esse controle também afeta outros alarmes do celular, o que está explicado na tela. Nenhum volume é alterado automaticamente no horário da dose.
- “Ouvir prévia da voz”, que lê o primeiro medicamento realmente cadastrado; se a lista estiver vazia, lê apenas uma apresentação genérica.
- “Configurar voz do celular”, para as opções locais de síntese de fala.

A notificação de medicamento inclui “Ouvir lembrete”. O início automático do serviço em segundo plano depende das exceções do Android, especialmente de alarmes exatos solicitados pelo usuário. Se o sistema negar o início, a notificação escrita continua disponível, com a ação explícita para ouvir. Volume zero, configurações de Não Perturbe, chamadas, roteamento para fones/Bluetooth e políticas do fabricante podem impedir que a fala seja ouvida no alto-falante. O aplicativo não tenta contornar essas preferências nem garante volume alto em qualquer condição.

Referências oficiais: [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [restrições de serviços em segundo plano](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) e [áudio em segundo plano no Android 17](https://developer.android.com/about/versions/17/changes/bg-audio). Como o projeto tem targetSdk 37, a leitura usa serviço mediaPlayback e atributos USAGE_ALARM.

Arquivos novos: `notifications/VozLembreteService.kt` e `notifications/TextoDaDose.kt`, dentro de `app/src/main/java/com/example/medhoraxml/`. Alterados: `Medicamento.kt`, `MedicamentoRepository.kt`, `AgendadorLembretes.kt`, `Notificacoes.kt`, `ConfiguracoesFragment.kt`, `fragment_configuracoes.xml`, `strings.xml`, `AndroidManifest.xml`, `README.md` e esta documentação.

A verificação nesta entrega é estática. Compilação, testes, geração de APK e execução em dispositivo não foram feitos pelo agente; a voz e o volume precisam ser conferidos no aparelho.

## Revisão e pendências

Foi feita somente revisão estática dos arquivos: leitura dos XML, referências locais de recursos e consulta dos estilos/atributos no AAR Material já existente no ambiente. Não foram executados compilação, testes, Gradle, emuladores ou comandos Git. O projeto Compose não foi acessado nem alterado.

Permanece pendente a conferência visual em dispositivo e a validação de execução, conforme as restrições desta etapa. Para reproduzir exatamente o calendário ilustrado do onboarding e os ícones rasterizados da referência, faltam esses assets individuais ou camadas editáveis no Figma. O onboarding usa o logotipo oficial com relógio, cápsula e sino; não foi criada uma nova marca. Não há alegação de correspondência pixel a pixel de tipografia e espaçamentos, pois a referência não contém esses estilos editáveis.

## Arquivos modificados

- `README.md`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/medhoraxml/MainActivity.kt`
- `app/src/main/java/com/example/medhoraxml/data/model/Medicamento.kt`
- `app/src/main/java/com/example/medhoraxml/data/repository/MedicamentoRepository.kt`
- `app/src/main/java/com/example/medhoraxml/ui/cadastro/CadastroFragment.kt`
- `app/src/main/java/com/example/medhoraxml/ui/detalhes/DetalhesFragment.kt`
- `app/src/main/java/com/example/medhoraxml/ui/historico/HistoricoFragment.kt`
- `app/src/main/java/com/example/medhoraxml/ui/home/HomeFragment.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/fragment_home.xml`
- `app/src/main/res/layout/fragment_cadastro.xml`
- `app/src/main/res/layout/fragment_detalhes.xml`
- `app/src/main/res/layout/fragment_historico.xml`
- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`

## Arquivos criados

- `app/src/main/java/com/example/medhoraxml/ui/cadastro/CadastroViewModel.kt`
- `app/src/main/java/com/example/medhoraxml/ui/common/DoseAdapter.kt`
- `app/src/main/java/com/example/medhoraxml/ui/common/MedicamentoAdapter.kt`
- `app/src/main/java/com/example/medhoraxml/ui/common/Ui.kt`
- `app/src/main/java/com/example/medhoraxml/ui/configuracoes/ConfiguracoesFragment.kt`
- `app/src/main/java/com/example/medhoraxml/ui/home/DoseConfirmadaDialog.kt`
- `app/src/main/java/com/example/medhoraxml/ui/medicamentos/MedicamentosFragment.kt`
- `app/src/main/java/com/example/medhoraxml/ui/welcome/OnboardingFragment.kt`
- `app/src/main/java/com/example/medhoraxml/ui/welcome/SplashFragment.kt`
- `app/src/main/res/color/day_text.xml`
- `app/src/main/res/color/input_stroke.xml`
- `app/src/main/res/color/nav_item.xml`
- `app/src/main/res/drawable-nodpi/medhora_logo.png`
- `app/src/main/res/drawable/bg_card.xml`
- `app/src/main/res/drawable/bg_day.xml`
- `app/src/main/res/drawable/bg_field.xml`
- `app/src/main/res/drawable/bg_icon.xml`
- `app/src/main/res/drawable/bg_splash.xml`
- `app/src/main/res/drawable/ic_add_circle.xml`
- `app/src/main/res/drawable/ic_back.xml`
- `app/src/main/res/drawable/ic_bell.xml`
- `app/src/main/res/drawable/ic_capsule.xml`
- `app/src/main/res/drawable/ic_capsule_green.xml`
- `app/src/main/res/drawable/ic_capsule_lilac.xml`
- `app/src/main/res/drawable/ic_capsule_red.xml`
- `app/src/main/res/drawable/ic_capsule_yellow.xml`
- `app/src/main/res/drawable/ic_check.xml`
- `app/src/main/res/drawable/ic_chevron.xml`
- `app/src/main/res/drawable/ic_clock.xml`
- `app/src/main/res/drawable/ic_delete.xml`
- `app/src/main/res/drawable/ic_edit.xml`
- `app/src/main/res/drawable/ic_home.xml`
- `app/src/main/res/drawable/ic_info.xml`
- `app/src/main/res/drawable/ic_list.xml`
- `app/src/main/res/drawable/ic_plus.xml`
- `app/src/main/res/drawable/ic_search.xml`
- `app/src/main/res/drawable/ic_settings.xml`
- `app/src/main/res/layout/dialog_dose_confirmada.xml`
- `app/src/main/res/layout/fragment_configuracoes.xml`
- `app/src/main/res/layout/fragment_medicamentos.xml`
- `app/src/main/res/layout/fragment_onboarding.xml`
- `app/src/main/res/layout/fragment_splash.xml`
- `app/src/main/res/layout/item_day.xml`
- `app/src/main/res/layout/item_dose.xml`
- `app/src/main/res/layout/item_history_header.xml`
- `app/src/main/res/layout/item_medicamento.xml`
- `app/src/main/res/layout/item_time.xml`
- `app/src/main/res/menu/bottom_navigation.xml`
- `app/src/main/res/menu/header_menu.xml`
- `docs/IMPLEMENTACAO.md`
- `docs/prototipo-medhora.png`
