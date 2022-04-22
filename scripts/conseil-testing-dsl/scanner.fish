function scan-line
    set -gx line_state $argv
    set -gx raw_line $argv
    set -gx codeTokens 0
    set -gx foundCode 1
    set -gx TOKENS N Q S Math

    set -gx system_token "S.[a-z]+"
    set -gx query_token "Q.[a-z_]+"
    set -gx node_token "N.[a-z]+"
    set -gx math_token "Math.[0-9\.]+"
    set -gx all_command_tokens (string join '|' $system_token $query_token $node_token $math_token)
    scan $argv
end

function scan
    set -gx parseCode '[]'
    while test (check_code_token $line_state) -eq $foundCode
        set -gx parseCode (generate-code-token $parseCode)
    end
    set codeTokensLength (math (echo $parseCode | jq '.|length' ) - 1)
    if test $codeTokensLength -gt -1
        for i in (seq 0 1 $codeTokensLength)
            set thisCodeToken (echo $parseCode | jq ".[$i]")
            set commandTokens (generate-command-tokens $thisCodeToken)
            set -gx parseCode (echo $parseCode | jq ".[$i].interpret = [$commandTokens | .[]]")
        end

        echo $parseCode | jq -c '.'

    else
        echo "[{ \"sub\": \"#1\", \"typeToken\" : \"UNIT\", \"valueToken\" : \"$raw_line\" , \"state\": \"#1\"}]"
    end


    #set cmdToken (echo $parseCode | jq '.[1]')
    #generate-command-tokens $cmdToken
    ##interpretToken $cmdToken

end

function check_code_token
    echo $argv | gsed -E '/#([^#]*)##/!{q1}' >/dev/null
    if test $status -eq 1
        math 0
    else
        echo $foundCode
    end
end

function check-node-token
    echo $argv | gsed -E '/N.[a-z]+/!{q1}' >/dev/null
    if test $status -eq 1
        math 0
    else
        echo $foundCode
    end
end

function check-query-token
    echo $argv | gsed -E '/Q.[a-z]+/!{q1}' >/dev/null
    if test $status -eq 1
        math 0
    else
        echo $foundCode
    end
end
function check-math-token
    echo $argv | gsed -E '/MATH.[0-9]+/!{q1}' >/dev/null
    if test $status -eq 1
        math 0
    else
        echo $foundCode
    end
end
function check-token
    echo $argv[2] | gsed -E "/$argv[1]/!{q1}" >/dev/null
    if test $status -eq 1
        math 0
    else
        echo $foundCode
    end
end

function generate-command-tokens
    set code (echo $argv | jq '.valueToken')
    set -gx command_state $code
    set -gx commandTokens 0
    set -gx parseCommand '[]'

    while test (check-token $all_command_tokens $command_state) -eq $foundCode
        set -gx parseCommand (generate-command-token $all_command_tokens $parseCommand)
    end
    echo $parseCommand | jq -c '.'
    #breakpoint
end

function generate-command-token
    set command_token_split (echo $command_state | gsed -E "s/($argv[1])/@\1@/" | string split @)
    set token $command_token_split[2]
    set interpret $token
    set or_tokens (string join '|' $TOKENS)
    set value (echo $token |  gsed -E "s/(^[$or_tokens]+)\.(.*)/\2/")
    set token (echo $token |  gsed -E "s/(^[$or_tokens]+)\.(.*)/\1/")
    set -gx commandTokens (math $commandTokens + 1)
    set command_token_split[2] "#$commandTokens"
    set subId "#$commandTokens"
    set -gx command_state (string join '' $command_token_split)
    echo $argv[2] | jq -c " .[. | length] |= { sub: \"$command_token_split[2]\" , typeToken: \"$token\", valueToken: \"$value\" , interpret: \"$interpret\", state: $command_state }"
    #breakpoint
end

function generate-code-token
    set code_token_split (echo $line_state | gsed -E 's/#([^#]*)##/@\1@/' | string split @)
    set token $code_token_split[2]
    set -gx codeTokens (math $codeTokens + 1)
    set code_token_split[2] "#$codeTokens"
    set -gx line_state (string join '' $code_token_split)
    echo $argv | jq -c " .[. | length] |= { sub: \"$code_token_split[2]\" , typeToken: \"code\", valueToken: \"$token\", interpret: \"#$token##\" , state: \"$line_state\" }"
end

function generate-line-tokens
    echo '[]' | jq -c " .[. | length] |= { line: \"\" , token: [] }"
end

function unqoute
    read -l qoutes
    echo $qoutes | sed -E 's/"//g'
end

function interpretToken
    set substitute (echo $argv | jq '.sub' | unqoute)
    set withValue (echo $argv | jq '.interpret' | unqoute)
    set state (echo $argv | jq '.state')
    #echo "sub: $substitute , withValue: $withValue , stateBefore: $state"
    echo $state | sed -E "s/$substitute/$withValue/"
    #breakpoint
end

function interpret
    function S
    end
    function Q
    end
    function N
    end
    function Math
    end
    set type (echo $argv | jq '.typeToken')
    set value (echo $argv | jq '.valueToken')
    switch $type
        case S
            S $value
        case Q
            Q $value
        case N
            N $value
        case Math
            Math $value
    end
end

function dependency
    set -gx TRUE true
    set -gx FALSE false
    getopts $argv | while read -l key value
        switch $key
            case a ast
                set ast $value
            case t typetoken
                set typeToken $value
        end
    end
    echo $ast | jq -c "isempty(.[] | .interpret[] | select(.typeToken == \"$typeToken\") ) | not"

end
