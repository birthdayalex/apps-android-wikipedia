( function() {

    var Bridge = function() {
        this.eventHandlers = {};
    };

    Bridge.prototype.handleMessage = function( type, payload ) {
        var that = this;
        if ( this.eventHandlers.hasOwnProperty( type ) ) {
            this.eventHandlers[type].forEach( function( callback ) {
                callback.call( that, payload );
            } );
        }
    };

    Bridge.prototype.registerListener = function( messageType, callback ) {
        if ( this.eventHandlers.hasOwnProperty( messageType ) ) {
            this.eventHandlers[messageType].push( callback );
        } else {
            this.eventHandlers[messageType] = [ callback ];
        }
    };

    Bridge.prototype.sendMessage = function( messageType, payload ) {
        var messagePack = { type: messageType, payload: payload };
        return prompt( JSON.stringify( messagePack) );
    };

    window.bridge = new Bridge();

    window.onload = function() {
        bridge.sendMessage( "DOMLoaded", {} );
    };
} )();