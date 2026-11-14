# Traço Livre — Android

Aplicativo de desenho Android nativo, gratuito e offline.

**Desenvolvido por Cristiano Aguilar**  
**Versão do aplicativo:** 1.4 (versionCode 5)  
**Pacote:** `br.com.cristianoaguilar.tracolivre`

## Recursos desta versão

- Lápis, borracha e cinco estilos de pincel: lápis clássico, marcador, aquarela suave, giz e spray.
- Paleta de cores e controle de espessura.
- Linha, retângulo, círculo e texto.
- Camadas independentes, com seleção, renomeação, visibilidade e exclusão.
- Desfazer e refazer por camada.
- Zoom por pinça e movimentação com dois dedos.
- Balde de tinta com tolerância para cores próximas.
- Importação de imagens, galeria interna em PNG e exportação PNG.
- Recuperação automática da imagem composta ao reabrir o aplicativo. A recuperação automática preserva o visual, mas não reconstrói camadas ou histórico.
- Não solicita permissão de internet.

## Compilar pelo GitHub Actions (método recomendado)

O workflow foi alinhado à mesma combinação de ferramentas usada na compilação bem-sucedida do aplicativo **Minha Feira**: **Temurin JDK 17 + Gradle 8.9 + Android SDK Platform 35 + Build Tools 35.0.0**. O projeto usa Android Gradle Plugin 8.7.3, compatível com Gradle 8.9.

1. Baixe este ZIP e extraia a pasta `TracoLivre`.
2. No GitHub, crie ou abra o repositório `TracoLivre`.
3. Envie **o conteúdo interno** da pasta `TracoLivre` para a raiz do repositório. Não envie apenas o ZIP. Inclua a pasta oculta `.github`.
4. Abra **Actions** e selecione **Gerar APK - Traco Livre**.
5. Clique em **Run workflow** e confirme em **Run workflow** novamente.
6. Aguarde o trabalho **Compilar APK Android** terminar com marca verde.
7. Na página da execução, em **Artifacts**, baixe `Traco-Livre-APK-v6`.
8. Extraia o ZIP baixado e instale `app-debug.apk` no Android. Se necessário, permita a instalação pelo navegador ou gerenciador de arquivos.

Não é necessário instalar Android Studio no computador ou no celular.

## Compilação local (opcional)

Requer JDK 17, Gradle 8.9 e Android SDK Platform 35. Na raiz do projeto:

```bash
gradle --no-daemon --stacktrace :app:assembleDebug
```

APK esperado: `app/build/outputs/apk/debug/app-debug.apk`.

## Observações de entrega

Este pacote contém o código-fonte e o workflow de compilação. O APK só deve ser considerado gerado quando a execução do GitHub Actions terminar com sucesso e o artefato estiver disponível. Esta entrega não declara compilação nem teste em aparelho: o ambiente utilizado para preparar o ZIP não dispõe de Gradle nem Android SDK.
