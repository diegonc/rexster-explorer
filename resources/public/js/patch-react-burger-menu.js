(function () {
  var _createClass = (function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; })();

  var _get = function get(_x, _x2, _x3) { var _again = true; _function: while (_again) { var object = _x, property = _x2, receiver = _x3; _again = false; if (object === null) object = Function.prototype; var desc = Object.getOwnPropertyDescriptor(object, property); if (desc === undefined) { var parent = Object.getPrototypeOf(object); if (parent === null) { return undefined; } else { _x = parent; _x2 = property; _x3 = receiver; _again = true; desc = parent = undefined; continue _function; } } else if ("value" in desc) { return desc.value; } else { var getter = desc.get; if (getter === undefined) { return undefined; } return getter.call(receiver); } } };

  function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

  function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

  window.patch_initially_opened_prop = function (menu) {
    return (function (_menu) {
      _inherits(_class, _menu);

      function _class() {
        _classCallCheck(this, _class);

        _get(Object.getPrototypeOf(_class.prototype), "constructor", this).apply(this, arguments);
      }

      _createClass(_class, [{
        key: "getInitialState",
        value: function getInitialState() {
          return { isOpen: this.props.initiallyOpened === true };
        }
      }]);

      return _class;
    })(menu);
  };

  window.patch_enhance_sanfona_accordion = function (accordion) {
    return (function (_accordion) {
      _inherits(_class2, _accordion);

      function _class2() {
        _classCallCheck(this, _class2);

        _get(Object.getPrototypeOf(_class2.prototype), "constructor", this).apply(this, arguments);
      }

      _createClass(_class2, [{
        key: "componentWillReceiveProps",
        value: function componentWillReceiveProps(nextProps) {
          if (nextProps.resetSelected) {
            var selectedIndex = nextProps.selectedIndex || 0;
            var state = { selectedIndex: selectedIndex };
            if (nextProps.allowMultiple) {
              state.activeItems = [selectedIndex];
            }
            this.setState(state);
          }
        }
      }, {
        key: "handleClick",
        value: function handleClick(index) {
          _get(Object.getPrototypeOf(_class2.prototype), "handleClick", this).call(this, index);
          if (this.props.onItemActivation) this.props.onItemActivation(index);
        }
      }]);

      return _class2;
    })(accordion);
  };
})();
