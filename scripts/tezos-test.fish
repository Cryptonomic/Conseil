set idx (math $idx + 1)
function tezos-test
    set -gx name tezos
    create-path
    getopts $argv | while read -l key value
        switch $key
            case n network
                set -gx network $value
            case k key
                set -gx apikey $value
            case h host
                set -gx host $value
            case N node
                set -gx node $value
        end

    end
    test-get-path (platforms)
    test-get-path (networks)
    test-get-path (entities)
    test-get-path (info)
    test-get-path (block_head)
    test-get-path (blocks_protocol)
    test-get-path (acounts_manager)
    test-get-path (accounts_attributes)
    test-get-path (operation_groups)
    test-get-path (operations_kind)
    test_accounts 2
    test_accounts 3

end

function create-path
    set -gx DIR (string join '' 'tezos' '-' 'test' '/' $host '/' $network $argv[1])
    mkdir -p (string join '' $name '-' 'test' '/' $host '/' $network $argv[1])
end

function path
    string join '' $argv[1]
end

function remove-double-qoute
    echo $argv[1] | sed -E 's/"//g'
end

function get-head-level
    get (block_head) | jq '.level'
end

function get
    curl -s --request GET --header (string join '' 'apiKey' ':' ' ' $apikey) \
        --url (string join '' $host $argv[1])
end

function post
    curl -s -H 'Content-Type: application/json' -H 'apiKey: hooman' \
        -X POST $argv[1] \
        -d $argv[2]
end
function error
    pastel paint "#ffffff" --on red $argv[1]
end

function highlight
    pastel paint green --on yellow $argv[1]
end

function highlight2
    pastel paint yellow --on grey $argv[1]
end
function ok
    pastel paint white --on green $argv[1]
end
function test_accounts
    set block_level (get-head-level)
    set top_accounts (query $argv)
    set count (echo $top_accounts | jq '. | length')
    set idx 0
    ## breakpoint

    read -l -P "Query $title ...." confirm
    echo $query | jq '.'
    read -l -P "...." confirm
    while test $idx -lt $count
        set this_account (echo $top_accounts | jq ".[$idx] | .account_id")
        set this_balance (echo $top_accounts | jq ".[$idx] | .balance")
        set node_result (node (spendable-account-balance-at-block (remove-double-qoute $this_account) $block_level))
        set node_account_balance (remove-double-qoute $node_result)
        echo "account: $this_account, conseil_balance: $this_balance, node_balance: $node_account_balance "
        if test $this_balance -ne $node_account_balance
            echo "Balance don't match between node and conseil for account $this_account"
            set diff (math $this_balance - $node_account_balance)
            set percent (math $diff / $node_account_balance x 100)
            error "difference of $percent%"
        else

            ok OK
        end
        set idx (math $idx + 1)
    end

end

function spendable-account-balance-at-block
    string join / chains main blocks $argv[2] context contracts $argv[1] balance
end
function account-balance-at-block-2
    switch $network
        case mainnet
            set balance balance
        case ithacanet
            set balance full_balance
    end
    set variable $value
    string join / chains main blocks $argv[2] context delegates $argv[1] $balance
end

function node
    curl -s (string join '' $node '/' $argv[1])
end

function query

    set -l qfile "queries/queries.json"
    set -gx query (jq -c ".[$argv[1]] | .query" $qfile)
    set -gx title (jq -c ".[$argv[1]] | .title" $qfile)
    set -l query_path (process_path (jq ".[$argv[1]] | .path" $qfile))
    set -gx full_path (string join ''  $host $query_path)
    post $full_path $query

end

function process_path
    remove-double-qoute (echo "$argv[1]" | sed -E "s/<network>/$network/")
end
function test-post-paths
    function process_path
        remove-double-qoute (echo "$argv[1]" | sed -E "s/<network>/$network/")
    end

    set -l qfile "queries/queries.json"
    set -gx query (jq -c '.[1] | .query' $qfile)
    set -l query_path (process_path (jq '.[1] | .path' $qfile))
    set -gx full_path (string join '' $host $query_path)
    function post
        curl -H 'Content-Type: application/json' -H 'apiKey: hooman' \
            -X POST $full_path \
            -d $query
    end
    echo "path $query_path \n full path : $full_path"
    post >query_test_1.json
    jq '.' query_test_1.json

end


function test-get-path
    set Path $argv[1]
    create-path $Path
    set file (string join '' $DIR '/' 'test.json')
    get $Path >$file
    set char_count (wc -c $file | awk '{print $1}')
    set json_length (jq '. | length' $file)
    highlight (string join '' "Get " ": " $Path)
    echo ""
    highlight2 (string join '' "Network " ": " $network)
    echo ""
    ok "CONSEIL RESULT: "
    jq '.' $file -C | less -R
    if test $char_count -lt 5
        echo "issue with path $Path : character count  $char_count"
    end


end

function platforms
    path /v2/metadata/platforms
end


function entities
    path (string join '/'  '/v2' 'metadata' $name $network 'entities')
end


function info
    path /info
end

function block_head
    path (string join '/'  '/v2' 'data' $name $network 'blocks' 'head')
end

function operation_groups
    path (string join '/'  '/v2' 'data' $name $network 'operation_groups')
end

function networks
    path (string join '/'  '/v2' 'metadata' $name 'networks')
end
function blocks_protocol
    path (string join '/'  '/v2' 'metadata' $name $network 'blocks' 'protocol')
end
function operations_kind
    path (string join '/'  '/v2' 'metadata' $name $network 'operations' 'kind')
end
function acounts_manager
    path (string join '/'  '/v2' 'metadata' $name $network 'accounts' 'account_id' 'tz1c')
end
function accounts_attributes
    path (string join '/'  '/v2' 'metadata' $name $network 'accounts' 'attributes')
end
