source fetch.fish

function setConseilLevel
    set -gx ConseilLevel (conseilHeadLevel)
end


function getConseilLevel
    echo $ConseilLevel
end

function unsetConseilLevel
    set -e ConseilLevel
end
