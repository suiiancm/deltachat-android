read -p "Push res/values/strings.xml to transifex? Press ENTER to continue, CTRL-C to abort."
tx push -s
./scripts/check-translations.sh
