import classNames from 'classnames';
import logo from './logo.png';

const Logo: GenieType.FC<{
  hideSplit?: boolean;
}> = (props) => {
  const { className, hideSplit } = props;

  return <div className={classNames('flex items-center', className)}>
    <img src={logo} alt="logo" width={20}/>
    <div className="ml-8 text-[16px] font-semibold text-brand">
      Genie
    </div>
    {!hideSplit && <div className="w-1 h-16 mx-8 bg-border"></div>}
  </div>;
};

export default Logo;
