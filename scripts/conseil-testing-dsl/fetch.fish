function get
    getopts $argv | while read -l key value
        switch $key
            case h host
                set FHOST $value
            case p path
                set FPATH $value
            case k key
                set FapiKey $value
        end
    end

    if not set -q FHOST
        set FHOST $CHOST
    end

    if not set -q FapiKey
        set FapiKey $apiKey
    end


    echo curl -s --request GET --header (string join '' 'apiKey' ':' ' ' $FapiKey) \
        --url (string join '' $FHOST $FPATH) >> curl.md

    curl -s --request GET --header (string join '' 'apiKey' ':' ' ' $FapiKey) \
        --url (string join '' $FHOST $FPATH)
end

function post
    getopts $argv | while read -l key value
        switch $key
            case h host
                set FHOST $value
            case p FPATH
                set FPATH $value
            case k key
                set FapiKey $value
            case q query
                set FQUERY $value
        end
    end

    if not set -q FHOST
        set FHOST $CHOST
    end

    if not set -q FapiKey
        if set -q apiKey
            set FapiKey $apiKey
        else
            set FapiKey hooman
        end
    end



    echo curl -s -H 'Content-Type: application/json' -H (string join '' 'apiKey' ':' ' ' 'hooman') \
    -X POST (string join ''  $FHOST $FPATH)  -d "$FQUERY" >> curl.md
    #echo $postcommand
     
    curl -s -H 'Content-Type: application/json' -H (string join '' 'apiKey' ':' ' ' 'hooman') \
        -X POST (string join ''  $FHOST $FPATH) \
        -d "$FQUERY"

    # breakpoint
end

function node
    curl -s (string join '' $NHOST $argv[1])
end


function conseilHeadLevel
    get -p (block-head) | jq '.level' | remove-double-qoutes
end

function data
    string join / /v2 data $CHAIN $CNETWORK (string join '/' $argv)
end


function block-head
    data blocks head
end

function platforms
    FPATH /v2/metadata/platforms
end


function entities
    FPATH (string join '/'  '/v2' 'metadata' $CHAIN $CNETWORK 'entities')
end


function info
    FPATH /info
end



function operation_groups
    FPATH (string join '/'  '/v2' 'data' $CHAIN $CNETWORK 'operation_groups')
end

function networks
    FPATH (string join '/'  '/v2' 'metadata' $CHAIN 'networks')
end
function blocks_protocol
    FPATH (string join '/'  '/v2' 'metadata' $CHAIN $CNETWORK 'blocks' 'protocol')
end
function operations_kind
    FPATH (string join '/'  '/v2' 'metadata' $CHAIN $CNETWORK 'operations' 'kind')
end
function acounts_manager
    FPATH (string join '/'  '/v2' 'metadata' $CHAIN $CNETWORK 'accounts' 'account_id' 'tz1c')
end
function accounts_attributes
    FPATH (string join '/'  '/v2' 'metadata' $CHAIN $CNETWORK 'accounts' 'attributes')
end
