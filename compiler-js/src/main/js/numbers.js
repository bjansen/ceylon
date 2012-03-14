function initType(a,b,c,d,e,f,g,h,i,j,k,l);//IGNORE
function String$(x){};//IGNORE
function Boolean$(x){};//IGNORE
function Character(x){};//IGNORE
function inheritProto(a,b,c,d,e,f,g);//IGNORE
var Object$,Castable,Integral,Numeric,equal,smaller,larger,exports,$true,$false;//IGNORE

function Integer(value) {
    var that = new Integer.$$;
    that.value = value;
    return that;
}
initType(Integer, 'ceylon.language.Integer', Object$, Castable, Integral, Numeric);
inheritProto(Integer, Object$, '$Object$');
var Integer$proto = Integer.$$.prototype;
Integer$proto.getString = function() { return String$(this.value.toString()) }
Integer$proto.plus = function(other) { return Integer(this.value+other.value) }
Integer$proto.minus = function(other) { return Integer(this.value-other.value) }
Integer$proto.times = function(other) { return Integer(this.value*other.value) }
Integer$proto.divided = function(other) {
    var exact = this.value/other.value;
    return Integer((exact<0) ? Math.ceil(exact) : Math.floor(exact));
}
Integer$proto.remainder = function(other) { return Integer(this.value%other.value) }
Integer$proto.power = function(other) {
    var exact = Math.pow(this.value, other.value);
    return Integer((exact<0) ? Math.ceil(exact) : Math.floor(exact));
}
Integer$proto.getNegativeValue = function() { return Integer(-this.value) }
Integer$proto.getPositiveValue = function() { return this }
Integer$proto.equals = function(other) { return Boolean$(other && other.value===this.value) }
Integer$proto.compare = function(other) {
    return this.value===other.value ? equal
                                    : (this.value<other.value ? smaller:larger);
}
Integer$proto.getFloat = function() { return Float(this.value) }
Integer$proto.getInteger = function() { return this }
Integer$proto.getCharacter = function() { return Character(this.value); }
Integer$proto.getSuccessor = function() { return Integer(this.value+1) }
Integer$proto.getPredecessor = function() { return Integer(this.value-1) }
Integer$proto.getUnit = function() { return Boolean$(this.value === 1) }
Integer$proto.getZero = function() { return Boolean$(this.value === 0) }
Integer$proto.getFractionalPart = function() { return Integer(0); }
Integer$proto.getWholePart = function() { return this; }
Integer$proto.getSign = function() { return this.value > 0 ? Integer(1) : this.value < 0 ? Integer(-1) : Integer(0); }
Integer$proto.getHash = function() { return this; }

function $parseInteger(s) {
    var x = s.value;
    //xkcd.com/208/
    if ((x.indexOf('_') >= 0 ? x.match(/^[+-]?\d{1,3}(_\d{3})+[kMGPT]?$/g) : x.match(/^[+-]?\d+[kMGPT]?$/g)) === null) {
        return null;
    }
    x = x.replace("_", "");
    var mag = null;
    if (x.match(/[kMGTP]$/g) !== null) {
        mag = x[x.length-1];
        x = x.slice(0,-1);
    }
    var i = parseInt(x);
    var factor=1;
    switch(mag) {
        case 'P':factor*=1000;
        case 'T':factor*=1000;
        case 'G':factor*=1000;
        case 'M':factor*=1000;
        case 'k':factor*=1000;
    }
    return isNaN(i) ? null : Integer(i*factor);
}
function $parseFloat(s) { return Float(parseFloat(s.value)); }

function Float(value) {
    var that = new Float.$$;
    that.value = value;
    return that;
}
initType(Float, 'ceylon.language.Float', Object$, Castable, Numeric);
inheritProto(Float, Object$, '$Object$');
var Float$proto = Float.$$.prototype;
Float$proto.getString = function() { return String$(this.value.toString()) }
Float$proto.plus = function(other) { return Float(this.value+other.value) }
Float$proto.minus = function(other) { return Float(this.value-other.value) }
Float$proto.times = function(other) { return Float(this.value*other.value) }
Float$proto.divided = function(other) { return Float(this.value/other.value) }
Float$proto.power = function(other) { return Float(Math.pow(this.value, other.value)) }
Float$proto.getNegativeValue = function() { return Float(-this.value) }
Float$proto.getPositiveValue = function() { return this }
Float$proto.equals = function(other) { return Boolean$(other && other.value===this.value) }
Float$proto.compare = function(other) {
    if (other === null || other === undefined) { return larger; }
    return this.value===other.value ? equal
                                    : (this.value<other.value ? smaller:larger);
}
Float$proto.getFloat = function() { return this }
Float$proto.getInteger = function() { return Integer(parseInt(this.value.toFixed())); }
Float$proto.getWholePart = function() {
    var _p = this.value.toPrecision();
    var dot = _p.indexOf('.');
    return dot >= 0 ? Float(parseFloat(_p.slice(0, dot))) : this;
}
Float$proto.getFractionalPart = function() {
    var _p = this.value.toPrecision();
    var dot = _p.indexOf('.');
    return dot >= 0 ? Float(parseFloat(_p.slice(dot))) : Float(0.0);
}
Float$proto.getSign = function() { return this.value > 0 ? Integer(1) : this.value < 0 ? Integer(-1) : Integer(0); }
Float$proto.getHash = function() { return String$(this.value.toPrecision()).getHash(); }
Float$proto.getUndefined = function() { return isNaN(this.value) ? $true : $false; }
Float$proto.getFinite = function() { return this.value!==Infinity && this.value!==-Infinity && !isNaN(this.value) ? $true : $false; }
Float$proto.getInfinite = function() { return this.value===Infinity || this.value===-Infinity ? $true : $false; }

function getInfinity() { return Float(Infinity); }
//function getNegativeInfinity() { return Float(-Infinity); }

exports.Integer=Integer;
exports.Float=Float;
exports.getInfinity=getInfinity;
exports.parseInteger=$parseInteger;
exports.parseFloat=$parseFloat;
